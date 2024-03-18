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

import java.util.Optional;

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

    /*
     * The OpenTelemetry spec requires that attribute keys be unique for a given collection of attributes.
     * A conforming API implementation (like the Java one) if they allow calling setAttribute multiple times ensures this uniqueness constraint by
     * only taking the last value into account. https://opentelemetry.io/docs/specs/otel/common/#attribute-collections
     * It could be useful to set a given attribute key with `withSpanAttribute` at different points of the pipeline.
     * For example to set a default value for the whole pipeline and override it for a given stage.
     * This test verifies this behavior by checking that withSpanAttribute occurring at a later point in the pipeline
     * override previous values. Also, the child span structure is taken into account.
     * N.B. that using withSpanAttribute(target: 'PIPELINE_ROOT_SPAN') inside several parallel stages with the same key
     *      could be non-deterministic which value is taken. (whichever stage's withSpanAttribute step is executed last wins)
     */
    @Test
    public void testSimplePipelineWithWithSpanAttributeStepOverride() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "withSpanAttribute(key: 'build.tool', value: 'a', setOn: 'TARGET_AND_CHILDREN')\n" +
            "withSpanAttribute(key: 'build.tool', value: 'b', setOn: 'TARGET_AND_CHILDREN')\n" +
            "node() {\n" +
            "    stage('build') {\n" +
            "       xsh (label: 'release-script-1', script: 'echo ze-echo-1') \n" +
            "       withSpanAttribute(key: 'build.tool', value: 'c', target: 'PIPELINE_ROOT_SPAN', setOn: 'TARGET_AND_CHILDREN')\n" +
            "       withSpanAttribute(key: 'build.tool', value: 'd', target: 'PIPELINE_ROOT_SPAN')\n" +
            "       withSpanAttribute(key: 'build.tool', value: 'e', setOn: 'TARGET_AND_CHILDREN')\n" +
            "       withSpanAttribute(key: 'build.tool', value: 'f', setOn: 'TARGET_AND_CHILDREN')\n" +
            "       withSpanAttribute(key: 'build.tool', value: 'g', target: 'CURRENT_SPAN', setOn: 'TARGET_ONLY')\n" +
            "       xsh (label: 'release-script-2', script: 'echo ze-echo-2') \n" +
            "    }\n" +
            "}";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-simple-pipeline-with-with-span-attribute-step-override" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();

        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI, JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "release-script-1", "Stage: build", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "release-script-2", "Stage: build", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        { // value 'b' - overrides 'a' for child spans (implicit: of the root span)
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI.equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool, CoreMatchers.is("b"));
            SpanData actualSpanData2 = spans.breadthFirstStream().filter(sdw -> "release-script-1".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool2, CoreMatchers.is("b"));
        }

        { // value 'c' - overrides 'b' for child spans of the root span that haven't been created yet
            // Current phase attribute is also overridden.
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> "Phase: Run".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool, CoreMatchers.is("c"));
            SpanData actualSpanData2 = spans.breadthFirstStream().filter(sdw -> "Phase: Finalise".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool2, CoreMatchers.is("c"));
        }

        { // value 'd' - overrides 'c' for the root span only
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> rootSpanName.equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool, CoreMatchers.is("d"));
        }

        { // value 'f' - overrides 'c' and 'e' for child spans of the current span that haven't been created yet
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> "release-script-2".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool, CoreMatchers.is("f"));
        }

        { // value 'g' - overrides 'f' for the current span only
            SpanData actualSpanData = spans.breadthFirstStream().filter(sdw -> "Stage: build".equals(sdw.spanData.getName())).findFirst().get().spanData;
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            MatcherAssert.assertThat(actualBuildTool, CoreMatchers.is("g"));
        }

        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(9L));
    }

    /*
     * Block syntax is not supported for withSpanAttribute step.
     * To support it WithSpanAttributeStep would need return a StepExecution implementation from start.
     * This StepExecution implementation would have to call
     * getContext().newBodyInvoker().withCallback(...).start();
     *
     * However before implementing something like that, first would have to be clarified:
     * - whether a new span should be created
     * - how the target and setOn parameters should be handled in this case
     *   - should implicit behavior be different (eg. implicit TARGET_AND_CHILDREN)
     *   - are there combinations that don't make sense (eg. any that do not include setting the attributes on the children)
     *
     * It would probably be easier to implement a separate step
     * newSpan (name:'...', ...) {
     *   withSpanAttribute(key: 'x', value: 'y', setOn: 'TARGET_AND_CHILDREN')
     *   ...
     * }
     * and maybe add an additional enum for setOn: 'CHILDREN_ONLY'
     */
    @Test
    public void testSimplePipelineWithWithSpanAttributeStepBlock() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "node() {\n" +
            "    stage('build') {\n" +
            "       withSpanAttribute(key: 'build.tool', value: 'maven') {\n" +
            "          xsh (label: 'release-script', script: 'echo ze-echo-1') \n" +
            "       }\n" +
            "    }\n" +
            "}";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-simple-pipeline-with-with-span-attribute-step-block" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));
        // Build fails due to withSpanAttribute using a closure with error message:
        // org.jenkinsci.plugins.workflow.actions.ErrorAction$ErrorId: 934c5dc1-2de4-4df1-a6da-1390b0e95afe
        // java.lang.IllegalArgumentException: Expected named arguments but got [{key=build.tool, value=maven}, org.jenkinsci.plugins.workflow.cps.CpsClosure2@738f6fad]

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();

        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI, JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Stage: build", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);
        Optional<SpanDataWrapper> spanDataWrapper = spans.breadthFirstStream().filter(sdw -> "release-script".equals(sdw.spanData.getName())).findFirst();
        MatcherAssert.assertThat("no span for step", spanDataWrapper.isEmpty(), CoreMatchers.is(true));

        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(7L));
    }

}
