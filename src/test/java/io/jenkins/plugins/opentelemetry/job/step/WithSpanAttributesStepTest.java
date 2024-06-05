/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import com.github.rutledgepaulv.prune.Tree;
import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.BaseIntegrationTest;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.commons.lang3.SystemUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Test;

import static org.junit.Assume.assumeFalse;

public class WithSpanAttributesStepTest extends BaseIntegrationTest {

    @Test
    public void testSimplePipelineWithWithSpanAttributesStepMix() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "setSpanAttributes([spanAttribute(key: 'pipeline.type', value: 'release', target: 'PIPELINE_ROOT_SPAN')])\n" +
            "withSpanAttributes([spanAttribute(key: 'pipeline.importance', value: 'critical', target: 'PIPELINE_ROOT_SPAN')]) {\n" +
            "  node() {\n" +
            "    stage('build') {\n" +
            "       xsh (label: 'release-script-1', script: 'echo ze-echo-1') \n" +
            "       setSpanAttributes([spanAttribute(key: 'stage.type', value: 'build-java-maven', target: 'CURRENT_SPAN')])\n" +
            "       withSpanAttributes([spanAttribute(key: 'build.tool', value: 'maven')]) {\n" +
            "         xsh (label: 'release-script-2', script: 'echo ze-echo-2') \n" +
            "       }\n" +
            "    }\n" +
            "    stage('test') {\n" +
            "       xsh (label: 'test-script', script: 'echo ze-echo-3') \n" +
            "    }\n" +
            "  }\n" +
            "}";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-simple-pipeline-with-with-span-attributes-step-mix" + jobNameSuffix.incrementAndGet();
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

        { // attribute 'pipeline.importance' - TARGET_AND_CHILDREN, must contain the rest of the pipeline
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

    /*
     * The OpenTelemetry spec requires that attribute keys be unique for a given collection of attributes.
     * A conforming API implementation (like the Java one) if they allow calling setAttribute multiple times ensures this uniqueness constraint by
     * only taking the last value into account. https://opentelemetry.io/docs/specs/otel/common/#attribute-collections
     * It could be useful to set a given attribute key with `withSpanAttributes` at different points of the pipeline.
     * For example to set a default value for the whole pipeline and override it for a given stage.
     * This test verifies this behavior by checking that withSpanAttributes occurring at a later point in the pipeline
     * override previous values. Also, the child span structure is taken into account.
     * N.B. that using spanAttribute(target: 'PIPELINE_ROOT_SPAN') inside several parallel stages with the same key
     *      could be non-deterministic which value is taken. (whichever stage's withSpanAttributes step is executed last wins)
     */
    @Test
    public void testSimplePipelineWithWithSpanAttributesStepOverride() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "withSpanAttributes([spanAttribute(key: 'build.tool', value: 'a'), spanAttribute(key: 'build.tool', value: 'b')]) {\n" +
            "  withSpanAttributes([spanAttribute(key: 'build.tool', value: 'c', target: 'PIPELINE_ROOT_SPAN')]) {\n" +
            "    withSpanAttributes([spanAttribute(key: 'build.tool', value: 'd', target: 'PIPELINE_ROOT_SPAN')]) {\n" +
            "      node() {\n" +
            "        stage('build') {\n" +
            "          xsh (label: 'release-script-1', script: 'echo ze-echo-1') \n" +
            "          setSpanAttributes([spanAttribute(key: 'build.tool', value: 'e', target: 'CURRENT_SPAN')])\n" +
            "          setSpanAttributes([spanAttribute(key: 'build.tool', value: 'f', target: 'PIPELINE_ROOT_SPAN')])\n" +
            "          xsh (label: 'release-script-2', script: 'echo ze-echo-2') \n" +
            "        }\n" +
            "        stage('test') {\n" +
            "          xsh (label: 'test-script-1', script: 'echo ze-echo-3') \n" +
            "          withSpanAttributes([spanAttribute(key: 'build.tool', value: 'g')]) {\n" +
            "            withSpanAttributes([spanAttribute(key: 'build.tool', value: 'h')]) {\n" +
            "              xsh (label: 'test-script-2', script: 'echo ze-echo-4') \n" +
            "            }\n" +
            "          }\n" +
            "          xsh (label: 'test-script-3', script: 'echo ze-echo-5') \n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-simple-pipeline-with-with-span-attributes-step-override" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();

        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI, JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "release-script-1", "Stage: build", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "release-script-2", "Stage: build", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "test-script-1", "Stage: test", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "test-script-2", "Stage: test", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "test-script-3", "Stage: test", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        { // value 'd' - overrides 'a', 'b' and 'c' for the root span and child spans
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI.equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool, CoreMatchers.is("d"));
            SpanData actualSpanData2 = spans.breadthFirstStream().filter(sdw -> "Phase: Finalise".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool2, CoreMatchers.is("d"));
            SpanData actualSpanData3 = spans.breadthFirstStream().filter(sdw -> "release-script-1".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool3, CoreMatchers.is("d"));
            SpanData actualSpanData4 = spans.breadthFirstStream().filter(sdw -> "release-script-2".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool4 = actualSpanData4.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool4, CoreMatchers.is("d"));
            SpanData actualSpanData5 = spans.breadthFirstStream().filter(sdw -> "test-script-1".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool5 = actualSpanData5.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool5, CoreMatchers.is("d"));
            SpanData actualSpanData6 = spans.breadthFirstStream().filter(sdw -> "test-script-3".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool6 = actualSpanData6.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool6, CoreMatchers.is("d"));
        }

        { // value 'e' - overrides 'd' for the current span only
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> "Stage: build".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool, CoreMatchers.is("e"));
        }

        { // value 'f' - overrides 'd' for the root span only
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> rootSpanName.equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool, CoreMatchers.is("f"));
        }

        { // value 'h' - overrides 'd' and 'g' for the current span and child spans
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> "Stage: test".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool, CoreMatchers.is("h"));
            SpanData actualSpanData2 = spans.breadthFirstStream().filter(sdw -> "test-script-2".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool2, CoreMatchers.is("h"));
        }

        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(13L));
    }

    @Test
    public void testSimplePipelineWithWithSpanAttributesStepBlock() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "node() {\n" +
            "    stage('build') {\n" +
            "       withSpanAttributes([spanAttribute(key: 'build.tool', value: 'maven')]) {\n" +
            "          xsh (label: 'release-script', script: 'echo ze-echo-1') \n" +
            "       }\n" +
            "    }\n" +
            "}";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-simple-pipeline-with-with-span-attributes-step-block" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();

        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI, JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Stage: build", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "release-script", "Stage: build", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        {
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> "Stage: build".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool, CoreMatchers.is("maven"));
            SpanData actualSpanData2 = spans.breadthFirstStream().filter(sdw -> "release-script".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool2, CoreMatchers.is("maven"));
        }

        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(8L));
    }

    @Test
    public void testDeclarativePipelineWithWithSpanAttributesStep1() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "pipeline {\n" +
            "    agent any\n" +
            "    options {\n" +
            "        withSpanAttributes([spanAttribute(key: 'pipeline.type', value: 'release', target: 'PIPELINE_ROOT_SPAN')]) \n" +
            "    }\n" +
            "    stages {\n" +
            "        stage('build') {\n" +
            "            steps {\n" +
            "                xsh (label: 'release-script', script: 'echo ze-echo-1')\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "}";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-declarative-pipeline-with-with-span-attributes-step" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();

        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI, JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Stage: build", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "release-script", "Stage: build", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        {
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> "Stage: build".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineType = actualSpanData.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            MatcherAssert.assertThat(actualPipelineType, CoreMatchers.is("release"));
            SpanData actualSpanData2 = spans.breadthFirstStream().filter(sdw -> "release-script".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineType2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            MatcherAssert.assertThat(actualPipelineType2, CoreMatchers.is("release"));
            SpanData actualSpanData3 = spans.breadthFirstStream().filter(sdw -> rootSpanName.equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineType3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            MatcherAssert.assertThat(actualPipelineType3, CoreMatchers.is("release"));
            SpanData actualSpanData4 = spans.breadthFirstStream().filter(sdw -> "Phase: Finalise".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineType4 = actualSpanData4.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            MatcherAssert.assertThat(actualPipelineType4, CoreMatchers.is("release"));
        }

        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(8L));
    }

    @Test
    public void testDeclarativePipelineWithWithSpanAttributesStep2() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "pipeline {\n" +
            "    agent any\n" +
            "    options {\n" +
            "        withSpanAttributes([spanAttribute(key: 'build.tool', value: 'maven')]) \n" +
            "        withSpanAttributes([spanAttribute(key: 'pipeline.type', value: 'release', target: 'PIPELINE_ROOT_SPAN')]) \n" +
            "    }\n" +
            "    stages {\n" +
            "        stage('build') {\n" +
            "            steps {\n" +
            "                xsh (label: 'release-script', script: 'echo ze-echo-1')\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "}";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-declarative-pipeline-with-with-span-attributes-step" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));
        // Build fails due to org.codehaus.groovy.control.MultipleCompilationErrorsException: startup failed:
        // WorkflowScript: 4: Duplicate option name: "withSpanAttributes" @ line 4, column 5.
        //       options {
        //       ^

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();

        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(4L));
    }

    @Test
    public void testDeclarativePipelineWithWithSpanAttributesStep() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "pipeline {\n" +
            "    agent any\n" +
            "    options {\n" +
            "        withSpanAttributes([spanAttribute(key: 'build.tool', value: 'maven'),spanAttribute(key: 'pipeline.type', value: 'release', target: 'PIPELINE_ROOT_SPAN')]) \n" +
            "    }\n" +
            "    stages {\n" +
            "        stage('build') {\n" +
            "            steps {\n" +
            "                xsh (label: 'release-script', script: 'echo ze-echo-1')\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "}";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-declarative-pipeline-with-with-span-attributes-step" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();

        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI, JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Stage: build", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "release-script", "Stage: build", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        {
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> "Stage: build".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineType = actualSpanData.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            MatcherAssert.assertThat(actualPipelineType, CoreMatchers.is("release"));
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool, CoreMatchers.is("maven"));
            SpanData actualSpanData2 = spans.breadthFirstStream().filter(sdw -> "release-script".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineType2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            MatcherAssert.assertThat(actualPipelineType2, CoreMatchers.is("release"));
            String actualBuildTool2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool2, CoreMatchers.is("maven"));
            SpanData actualSpanData3 = spans.breadthFirstStream().filter(sdw -> rootSpanName.equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineType3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            MatcherAssert.assertThat(actualPipelineType3, CoreMatchers.is("release"));
            String actualBuildTool3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool3, CoreMatchers.nullValue());
            SpanData actualSpanData4 = spans.breadthFirstStream().filter(sdw -> "Phase: Finalise".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualPipelineType4 = actualSpanData4.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            MatcherAssert.assertThat(actualPipelineType4, CoreMatchers.is("release"));
            String actualBuildTool4 = actualSpanData4.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool4, CoreMatchers.nullValue());
        }

        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(8L));
    }

}
