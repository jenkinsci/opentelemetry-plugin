/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import com.github.rutledgepaulv.prune.Tree;
import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.BaseIntegrationTest;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.commons.lang3.SystemUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Test;

import static org.junit.Assume.assumeFalse;

public class SetSpanAttributesStepTest extends BaseIntegrationTest {

    @Test
    public void testSimplePipelineWithSetSpanAttributesStep() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "setSpanAttributes([spanAttribute(key: 'pipeline.type', value: 'release', target: 'PIPELINE_ROOT_SPAN')])\n" +
            "node() {\n" +
            "    stage('build') {\n" +
            "       setSpanAttributes([spanAttribute(key: 'pipeline.importance', value: 'critical', target: 'PIPELINE_ROOT_SPAN')])\n" +
            "       setSpanAttributes([spanAttribute(key: 'stage.type', value: 'build-java-maven', target: 'CURRENT_SPAN')])\n" +
            "       setSpanAttributes([spanAttribute(key: 'build.tool', value: 'maven'), spanAttribute(key: 'test.tool', value: 'junit')])\n" +
            "       xsh (label: 'release-script', script: 'echo ze-echo-1') \n" +
            "    }\n" +
            "}";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-simple-pipeline-with-with-span-attribute-step" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = JenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();

        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, JenkinsAttributes.AGENT_ALLOCATION_UI, JenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "release-script", "Stage: build", JenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        { // attribute 'pipeline.type' - PIPELINE_ROOT_SPAN
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> rootSpanName.equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineType = actualSpanData.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            MatcherAssert.assertThat(actualPipelineType, CoreMatchers.is("release"));
        }

        { // attribute 'pipeline.importance' - PIPELINE_ROOT_SPAN, can be configured anywhere in the pipeline
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> rootSpanName.equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineImportance = actualSpanData.getAttributes().get(AttributeKey.stringKey("pipeline.importance"));
            MatcherAssert.assertThat(actualPipelineImportance, CoreMatchers.is("critical"));
        }

        { // attribute 'stage.type' - CURRENT_SPAN
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> "Stage: build".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualStageType = actualSpanData.getAttributes().get(AttributeKey.stringKey("stage.type"));
            MatcherAssert.assertThat(actualStageType, CoreMatchers.is("build-java-maven"));
        }

        { // attribute 'build.tool' - implicitly CURRENT_SPAN
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> "Stage: build".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool, CoreMatchers.is("maven"));
        }

        { // attribute 'test.tool' - implicitly CURRENT_SPAN, multiple spanAttributes specified in list
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> "Stage: build".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("test.tool"));
            MatcherAssert.assertThat(actualBuildTool, CoreMatchers.is("junit"));
        }

        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(8L));
    }

}
