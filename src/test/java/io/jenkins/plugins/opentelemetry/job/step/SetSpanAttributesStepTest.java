/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.github.rutledgepaulv.prune.Tree;
import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.BaseIntegrationTest;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;

class SetSpanAttributesStepTest extends BaseIntegrationTest {

    @Test
    void testSimplePipelineWithSetSpanAttributesStep() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            setSpanAttributes([spanAttribute(key: 'pipeline.type', value: 'release', target: 'PIPELINE_ROOT_SPAN')])
            node() {
                stage('build') {
                   setSpanAttributes([spanAttribute(key: 'pipeline.importance', value: 'critical', target: 'PIPELINE_ROOT_SPAN')])
                   setSpanAttributes([spanAttribute(key: 'stage.type', value: 'build-java-maven', target: 'CURRENT_SPAN')])
                   setSpanAttributes([spanAttribute(key: 'build.tool', value: 'maven'), spanAttribute(key: 'test.tool', value: 'junit')])
                   xsh (label: 'release-script', script: 'echo ze-echo-1')\s
                }
            }""";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-simple-pipeline-with-with-span-attribute-step" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getBuildTrace();

        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(
                spans,
                ExtendedJenkinsAttributes.AGENT_ALLOCATION_UI,
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run",
                rootSpanName);
        checkChainOfSpans(
                spans,
                "release-script",
                "Stage: build",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run",
                rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        { // attribute 'pipeline.type' - PIPELINE_ROOT_SPAN
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> rootSpanName.equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineType = actualSpanData.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            assertThat(actualPipelineType, is("release"));
        }

        { // attribute 'pipeline.importance' - PIPELINE_ROOT_SPAN, can be configured anywhere in the pipeline
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> rootSpanName.equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineImportance =
                    actualSpanData.getAttributes().get(AttributeKey.stringKey("pipeline.importance"));
            assertThat(actualPipelineImportance, is("critical"));
        }

        { // attribute 'stage.type' - CURRENT_SPAN
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> "Stage: build".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualStageType = actualSpanData.getAttributes().get(AttributeKey.stringKey("stage.type"));
            assertThat(actualStageType, is("build-java-maven"));
        }

        { // attribute 'build.tool' - implicitly CURRENT_SPAN
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> "Stage: build".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool, is("maven"));
        }

        { // attribute 'test.tool' - implicitly CURRENT_SPAN, multiple spanAttributes specified in list
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> "Stage: build".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("test.tool"));
            assertThat(actualBuildTool, is("junit"));
        }

        assertThat(spans.cardinality(), is(8L));
    }
}
