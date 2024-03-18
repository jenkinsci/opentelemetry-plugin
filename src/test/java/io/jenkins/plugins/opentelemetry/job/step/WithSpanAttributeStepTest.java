/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import static org.junit.Assume.assumeFalse;

import org.apache.commons.lang3.SystemUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Test;

import com.github.rutledgepaulv.prune.Tree;

import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.BaseIntegrationTest;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;

public class WithSpanAttributeStepTest extends BaseIntegrationTest {

    @Test
    public void testSimplePipelineWithWithSpanAttributeStepTarget() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "withSpanAttribute(key: 'pipeline.type', value: 'release', target: 'PIPELINE_ROOT_SPAN')\n" +
            "node() {\n" +
            "    stage('build') {\n" +
            "       withSpanAttribute(key: 'pipeline.importance', value: 'critical', target: 'PIPELINE_ROOT_SPAN')\n" +
            "       withSpanAttribute(key: 'stage.type', value: 'build-java-maven', target: 'CURRENT_SPAN')\n" +
            "       withSpanAttribute(key: 'build.tool', value: 'maven')\n" +
            "       xsh (label: 'release-script', script: 'echo ze-echo-1') \n" +
            "    }\n" +
            "}";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-simple-pipeline-with-with-span-attribute-step-target" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();

        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI, JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "release-script", "Stage: build", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
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

        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(8L));
    }

    @Test
    public void testSimplePipelineWithWithSpanAttributeStepSetOn() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "withSpanAttribute(key: 'pipeline.type', value: 'release', target: 'PIPELINE_ROOT_SPAN')\n" +
            "node() {\n" +
            "    stage('build') {\n" +
            "       xsh (label: 'release-script-1', script: 'echo ze-echo-1') \n" +
            "       withSpanAttribute(key: 'pipeline.importance', value: 'critical', target: 'PIPELINE_ROOT_SPAN', setOn: 'TARGET_AND_CHILDREN')\n" +
            "       withSpanAttribute(key: 'stage.type', value: 'build-java-maven', target: 'CURRENT_SPAN', setOn: 'TARGET_ONLY')\n" +
            "       withSpanAttribute(key: 'build.tool', value: 'maven', setOn: 'TARGET_AND_CHILDREN')\n" +
            "       xsh (label: 'release-script-2', script: 'echo ze-echo-2') \n" +
            "    }\n" +
            "    stage('test') {\n" +
            "       xsh (label: 'test-script', script: 'echo ze-echo-3') \n" +
            "    }\n" +
            "}";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-simple-pipeline-with-with-span-attribute-step-seton" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();

        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI, JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "release-script-1", "Stage: build", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "release-script-2", "Stage: build", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "test-script", "Stage: test", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        { // attribute 'pipeline.type' - implicitly TARGET_ONLY
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> rootSpanName.equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineType = actualSpanData.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            MatcherAssert.assertThat("attribute is set on target", actualPipelineType, CoreMatchers.is("release"));
            SpanData actualSpanData2 = spans.breadthFirstStream().filter(sdw -> "Stage: build".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineType2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            MatcherAssert.assertThat("attribute is not set on child", actualPipelineType2, CoreMatchers.nullValue());
            SpanData actualSpanData3 = spans.breadthFirstStream().filter(sdw -> "Phase: Run".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineType3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            MatcherAssert.assertThat("attribute is not set on child", actualPipelineType3, CoreMatchers.nullValue());
        }

        { // attribute 'pipeline.importance' - TARGET_AND_CHILDREN, can be configured anywhere in the pipeline
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> rootSpanName.equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineImportance = actualSpanData.getAttributes().get(AttributeKey.stringKey("pipeline.importance"));
            MatcherAssert.assertThat("attribute is set on target", actualPipelineImportance, CoreMatchers.is("critical"));
            SpanData actualSpanData2 = spans.breadthFirstStream().filter(sdw -> "Stage: build".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineImportance2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("pipeline.importance"));
            MatcherAssert.assertThat("attribute is set on child", actualPipelineImportance2, CoreMatchers.is("critical"));
            SpanData actualSpanData3 = spans.breadthFirstStream().filter(sdw -> "Stage: test".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineImportance3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("pipeline.importance"));
            MatcherAssert.assertThat("attribute is set on child", actualPipelineImportance3, CoreMatchers.is("critical"));
            SpanData actualSpanData4 = spans.breadthFirstStream().filter(sdw -> "Phase: Start".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineImportance4 = actualSpanData4.getAttributes().get(AttributeKey.stringKey("pipeline.importance"));
            MatcherAssert.assertThat("attribute is not set on closed child span", actualPipelineImportance4, CoreMatchers.nullValue());
            SpanData actualSpanData5 = spans.breadthFirstStream().filter(sdw -> "Phase: Run".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineImportance5 = actualSpanData5.getAttributes().get(AttributeKey.stringKey("pipeline.importance"));
            MatcherAssert.assertThat("attribute is set on child", actualPipelineImportance5, CoreMatchers.is("critical"));
            SpanData actualSpanData6 = spans.breadthFirstStream().filter(sdw -> "Phase: Finalise".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineImportance6 = actualSpanData6.getAttributes().get(AttributeKey.stringKey("pipeline.importance"));
            MatcherAssert.assertThat("attribute is set on child", actualPipelineImportance6, CoreMatchers.is("critical"));
        }

        { // attribute 'stage.type' - TARGET_ONLY
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> "Stage: build".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualStageType = actualSpanData.getAttributes().get(AttributeKey.stringKey("stage.type"));
            MatcherAssert.assertThat("attribute is set on target", actualStageType, CoreMatchers.is("build-java-maven"));
            SpanData actualSpanData2 = spans.breadthFirstStream().filter(sdw -> "test-script".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualStageType2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("stage.type"));
            MatcherAssert.assertThat("attribute is not set on child", actualStageType2, CoreMatchers.nullValue());
            SpanData actualSpanData3 = spans.breadthFirstStream().filter(sdw -> rootSpanName.equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualStageType3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("stage.type"));
            MatcherAssert.assertThat("attribute is not set on parent", actualStageType3, CoreMatchers.nullValue());
            SpanData actualSpanData4 = spans.breadthFirstStream().filter(sdw -> "Stage: test".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualStageType4 = actualSpanData4.getAttributes().get(AttributeKey.stringKey("stage.type"));
            MatcherAssert.assertThat("attribute is not set on sibling", actualStageType4, CoreMatchers.nullValue());
        }

        { // attribute 'build.tool' - TARGET_AND_CHILDREN
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> "Stage: build".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat("attribute is set on target", actualBuildTool, CoreMatchers.is("maven"));
            SpanData actualSpanData2 = spans.breadthFirstStream().filter(sdw -> "release-script-2".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat("attribute is set on child", actualBuildTool2, CoreMatchers.is("maven"));
            SpanData actualSpanData3 = spans.breadthFirstStream().filter(sdw -> rootSpanName.equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat("attribute is not set on parent", actualBuildTool3, CoreMatchers.nullValue());
            SpanData actualSpanData4 = spans.breadthFirstStream().filter(sdw -> "Stage: test".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool4 = actualSpanData4.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat("attribute is not set on sibling", actualBuildTool4, CoreMatchers.nullValue());
            SpanData actualSpanData5 = spans.breadthFirstStream().filter(sdw -> "Phase: Start".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool5 = actualSpanData5.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat("attribute is not set on parent", actualBuildTool5, CoreMatchers.nullValue());
            SpanData actualSpanData6 = spans.breadthFirstStream().filter(sdw -> "release-script-1".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool6 = actualSpanData6.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat("attribute is not set on closed child span", actualBuildTool6, CoreMatchers.nullValue());
        }

        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(11L));
    }

    // TODO: test for overriding same key

    // TODO: test for block withSpanAttribute(...) { xsh() }

    // TODO: Fix RuntimeException: Failed to serialize io.jenkins.plugins.opentelemetry.job.action.AttributeSetterAction#attributeKey for class io.jenkins.plugins.opentelemetry.job.action.AttributeSetterAction
}