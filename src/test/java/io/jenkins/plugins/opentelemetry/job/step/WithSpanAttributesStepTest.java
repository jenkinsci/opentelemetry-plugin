/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
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

class WithSpanAttributesStepTest extends BaseIntegrationTest {

    @Test
    void testSimplePipelineWithWithSpanAttributesStepMix() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            setSpanAttributes([spanAttribute(key: 'pipeline.type', value: 'release', target: 'PIPELINE_ROOT_SPAN')])
            withSpanAttributes([spanAttribute(key: 'pipeline.importance', value: 'critical', target: 'PIPELINE_ROOT_SPAN')]) {
              node() {
                stage('build') {
                   xsh (label: 'release-script-1', script: 'echo ze-echo-1')\s
                   setSpanAttributes([spanAttribute(key: 'stage.type', value: 'build-java-maven', target: 'CURRENT_SPAN')])
                   withSpanAttributes([spanAttribute(key: 'build.tool', value: 'maven')]) {
                     xsh (label: 'release-script-2', script: 'echo ze-echo-2')\s
                   }
                }
                stage('test') {
                   xsh (label: 'test-script', script: 'echo ze-echo-3')\s
                }
              }
            }""";
        jenkinsRule.createOnlineSlave();

        final String jobName =
                "test-simple-pipeline-with-with-span-attributes-step-mix" + jobNameSuffix.incrementAndGet();
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
                "release-script-1",
                "Stage: build",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run",
                rootSpanName);
        checkChainOfSpans(
                spans,
                "release-script-2",
                "Stage: build",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run",
                rootSpanName);
        checkChainOfSpans(
                spans, "test-script", "Stage: test", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        { // attribute 'pipeline.type' - implicitly TARGET_ONLY
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> rootSpanName.equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineType = actualSpanData.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            assertThat("attribute is set on target", actualPipelineType, is("release"));
            SpanData actualSpanData2 = spans.breadthFirstStream()
                .filter(sdw -> "Stage: build".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineType2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            assertThat("attribute is not set on child", actualPipelineType2, nullValue());
            SpanData actualSpanData3 = spans.breadthFirstStream()
                .filter(sdw -> "Phase: Run".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineType3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            assertThat("attribute is not set on child", actualPipelineType3, nullValue());
        }

        { // attribute 'pipeline.importance' - TARGET_AND_CHILDREN, must contain the rest of the pipeline
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> rootSpanName.equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineImportance =
                    actualSpanData.getAttributes().get(AttributeKey.stringKey("pipeline.importance"));
            assertThat(
                    "attribute is set on target", actualPipelineImportance, is("critical"));
            SpanData actualSpanData2 = spans.breadthFirstStream()
                .filter(sdw -> "Stage: build".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineImportance2 =
                    actualSpanData2.getAttributes().get(AttributeKey.stringKey("pipeline.importance"));
            assertThat(
                    "attribute is set on child", actualPipelineImportance2, is("critical"));
            SpanData actualSpanData3 = spans.breadthFirstStream()
                .filter(sdw -> "Stage: test".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineImportance3 =
                    actualSpanData3.getAttributes().get(AttributeKey.stringKey("pipeline.importance"));
            assertThat(
                    "attribute is set on child", actualPipelineImportance3, is("critical"));
            SpanData actualSpanData4 = spans.breadthFirstStream()
                .filter(sdw -> "Phase: Start".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineImportance4 =
                    actualSpanData4.getAttributes().get(AttributeKey.stringKey("pipeline.importance"));
            assertThat(
                    "attribute is not set on closed child span", actualPipelineImportance4, nullValue());
            SpanData actualSpanData5 = spans.breadthFirstStream()
                .filter(sdw -> "Phase: Run".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineImportance5 =
                    actualSpanData5.getAttributes().get(AttributeKey.stringKey("pipeline.importance"));
            assertThat(
                    "attribute is set on child", actualPipelineImportance5, is("critical"));
            SpanData actualSpanData6 = spans.breadthFirstStream()
                .filter(sdw -> "Phase: Finalise".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineImportance6 =
                    actualSpanData6.getAttributes().get(AttributeKey.stringKey("pipeline.importance"));
            assertThat(
                    "attribute is set on child", actualPipelineImportance6, is("critical"));
        }

        { // attribute 'stage.type' - TARGET_ONLY
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> "Stage: build".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualStageType = actualSpanData.getAttributes().get(AttributeKey.stringKey("stage.type"));
            assertThat(
                    "attribute is set on target", actualStageType, is("build-java-maven"));
            SpanData actualSpanData2 = spans.breadthFirstStream()
                .filter(sdw -> "test-script".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualStageType2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("stage.type"));
            assertThat("attribute is not set on child", actualStageType2, nullValue());
            SpanData actualSpanData3 = spans.breadthFirstStream()
                .filter(sdw -> rootSpanName.equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualStageType3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("stage.type"));
            assertThat("attribute is not set on parent", actualStageType3, nullValue());
            SpanData actualSpanData4 = spans.breadthFirstStream()
                .filter(sdw -> "Stage: test".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualStageType4 = actualSpanData4.getAttributes().get(AttributeKey.stringKey("stage.type"));
            assertThat("attribute is not set on sibling", actualStageType4, nullValue());
        }

        { // attribute 'build.tool' - TARGET_AND_CHILDREN
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> "Stage: build".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat("attribute is set on target", actualBuildTool, is("maven"));
            SpanData actualSpanData2 = spans.breadthFirstStream()
                .filter(sdw -> "release-script-2".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat("attribute is set on child", actualBuildTool2, is("maven"));
            SpanData actualSpanData3 = spans.breadthFirstStream()
                .filter(sdw -> rootSpanName.equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat("attribute is not set on parent", actualBuildTool3, nullValue());
            SpanData actualSpanData4 = spans.breadthFirstStream()
                .filter(sdw -> "Stage: test".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool4 = actualSpanData4.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat("attribute is not set on sibling", actualBuildTool4, nullValue());
            SpanData actualSpanData5 = spans.breadthFirstStream()
                .filter(sdw -> "Phase: Start".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool5 = actualSpanData5.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat("attribute is not set on parent", actualBuildTool5, nullValue());
            SpanData actualSpanData6 = spans.breadthFirstStream()
                .filter(sdw -> "release-script-1".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool6 = actualSpanData6.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(
                    "attribute is not set on closed child span", actualBuildTool6, nullValue());
        }

        assertThat(spans.cardinality(), is(11L));
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
    void testSimplePipelineWithWithSpanAttributesStepOverride() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            withSpanAttributes([spanAttribute(key: 'build.tool', value: 'a'), spanAttribute(key: 'build.tool', value: 'b')]) {
              withSpanAttributes([spanAttribute(key: 'build.tool', value: 'c', target: 'PIPELINE_ROOT_SPAN')]) {
                withSpanAttributes([spanAttribute(key: 'build.tool', value: 'd', target: 'PIPELINE_ROOT_SPAN')]) {
                  node() {
                    stage('build') {
                      xsh (label: 'release-script-1', script: 'echo ze-echo-1')\s
                      setSpanAttributes([spanAttribute(key: 'build.tool', value: 'e', target: 'CURRENT_SPAN')])
                      setSpanAttributes([spanAttribute(key: 'build.tool', value: 'f', target: 'PIPELINE_ROOT_SPAN')])
                      xsh (label: 'release-script-2', script: 'echo ze-echo-2')\s
                    }
                    stage('test') {
                      xsh (label: 'test-script-1', script: 'echo ze-echo-3')\s
                      withSpanAttributes([spanAttribute(key: 'build.tool', value: 'g')]) {
                        withSpanAttributes([spanAttribute(key: 'build.tool', value: 'h')]) {
                          xsh (label: 'test-script-2', script: 'echo ze-echo-4')\s
                        }
                      }
                      xsh (label: 'test-script-3', script: 'echo ze-echo-5')\s
                    }
                  }
                }
              }
            }""";
        jenkinsRule.createOnlineSlave();

        final String jobName =
                "test-simple-pipeline-with-with-span-attributes-step-override" + jobNameSuffix.incrementAndGet();
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
                "release-script-1",
                "Stage: build",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run",
                rootSpanName);
        checkChainOfSpans(
                spans,
                "release-script-2",
                "Stage: build",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run",
                rootSpanName);
        checkChainOfSpans(
                spans, "test-script-1", "Stage: test", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(
                spans, "test-script-2", "Stage: test", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(
                spans, "test-script-3", "Stage: test", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        { // value 'd' - overrides 'a', 'b' and 'c' for the root span and child spans
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> ExtendedJenkinsAttributes.AGENT_ALLOCATION_UI.equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool, is("d"));
            SpanData actualSpanData2 = spans.breadthFirstStream()
                .filter(sdw -> "Phase: Finalise".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool2, is("d"));
            SpanData actualSpanData3 = spans.breadthFirstStream()
                .filter(sdw -> "release-script-1".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool3, is("d"));
            SpanData actualSpanData4 = spans.breadthFirstStream()
                .filter(sdw -> "release-script-2".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool4 = actualSpanData4.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool4, is("d"));
            SpanData actualSpanData5 = spans.breadthFirstStream()
                .filter(sdw -> "test-script-1".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool5 = actualSpanData5.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool5, is("d"));
            SpanData actualSpanData6 = spans.breadthFirstStream()
                .filter(sdw -> "test-script-3".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool6 = actualSpanData6.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool6, is("d"));
        }

        { // value 'e' - overrides 'd' for the current span only
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> "Stage: build".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool, is("e"));
        }

        { // value 'f' - overrides 'd' for the root span only
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> rootSpanName.equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool, is("f"));
        }

        { // value 'h' - overrides 'd' and 'g' for the current span and child spans
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> "Stage: test".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool, is("h"));
            SpanData actualSpanData2 = spans.breadthFirstStream()
                .filter(sdw -> "test-script-2".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool2, is("h"));
        }

        assertThat(spans.cardinality(), is(13L));
    }

    @Test
    void testSimplePipelineWithWithSpanAttributesStepBlock() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            node() {
                stage('build') {
                   withSpanAttributes([spanAttribute(key: 'build.tool', value: 'maven')]) {
                      xsh (label: 'release-script', script: 'echo ze-echo-1')\s
                   }
                }
            }""";
        jenkinsRule.createOnlineSlave();

        final String jobName =
                "test-simple-pipeline-with-with-span-attributes-step-block" + jobNameSuffix.incrementAndGet();
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
        checkChainOfSpans(spans, "Stage: build", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(
                spans,
                "release-script",
                "Stage: build",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run",
                rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        {
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> "Stage: build".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool, is("maven"));
            SpanData actualSpanData2 = spans.breadthFirstStream()
                .filter(sdw -> "release-script".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualBuildTool2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool2, is("maven"));
        }

        assertThat(spans.cardinality(), is(8L));
    }

    @Test
    void testDeclarativePipelineWithWithSpanAttributesStep1() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            pipeline {
                agent any
                options {
                    withSpanAttributes([spanAttribute(key: 'pipeline.type', value: 'release', target: 'PIPELINE_ROOT_SPAN')])\s
                }
                stages {
                    stage('build') {
                        steps {
                            xsh (label: 'release-script', script: 'echo ze-echo-1')
                        }
                    }
                }
            }""";
        jenkinsRule.createOnlineSlave();

        final String jobName =
                "test-declarative-pipeline-with-with-span-attributes-step" + jobNameSuffix.incrementAndGet();
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
        checkChainOfSpans(spans, "Stage: build", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(
                spans,
                "release-script",
                "Stage: build",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run",
                rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        {
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> "Stage: build".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineType = actualSpanData.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            assertThat(actualPipelineType, is("release"));
            SpanData actualSpanData2 = spans.breadthFirstStream()
                .filter(sdw -> "release-script".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineType2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            assertThat(actualPipelineType2, is("release"));
            SpanData actualSpanData3 = spans.breadthFirstStream()
                .filter(sdw -> rootSpanName.equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineType3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            assertThat(actualPipelineType3, is("release"));
            SpanData actualSpanData4 = spans.breadthFirstStream()
                .filter(sdw -> "Phase: Finalise".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineType4 = actualSpanData4.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            assertThat(actualPipelineType4, is("release"));
        }

        assertThat(spans.cardinality(), is(8L));
    }

    @Test
    void testDeclarativePipelineWithWithSpanAttributesStep2() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            pipeline {
                agent any
                options {
                    withSpanAttributes([spanAttribute(key: 'build.tool', value: 'maven')])\s
                    withSpanAttributes([spanAttribute(key: 'pipeline.type', value: 'release', target: 'PIPELINE_ROOT_SPAN')])\s
                }
                stages {
                    stage('build') {
                        steps {
                            xsh (label: 'release-script', script: 'echo ze-echo-1')
                        }
                    }
                }
            }""";
        jenkinsRule.createOnlineSlave();

        final String jobName =
                "test-declarative-pipeline-with-with-span-attributes-step" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));
        // Build fails due to org.codehaus.groovy.control.MultipleCompilationErrorsException: startup failed:
        // WorkflowScript: 4: Duplicate option name: "withSpanAttributes" @ line 4, column 5.
        //       options {
        //       ^

        String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getBuildTrace();

        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        assertThat(spans.cardinality(), is(4L));
    }

    @Test
    void testDeclarativePipelineWithWithSpanAttributesStep() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            pipeline {
                agent any
                options {
                    withSpanAttributes([spanAttribute(key: 'build.tool', value: 'maven'),spanAttribute(key: 'pipeline.type', value: 'release', target: 'PIPELINE_ROOT_SPAN')])\s
                }
                stages {
                    stage('build') {
                        steps {
                            xsh (label: 'release-script', script: 'echo ze-echo-1')
                        }
                    }
                }
            }""";
        jenkinsRule.createOnlineSlave();

        final String jobName =
                "test-declarative-pipeline-with-with-span-attributes-step" + jobNameSuffix.incrementAndGet();
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
        checkChainOfSpans(spans, "Stage: build", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(
                spans,
                "release-script",
                "Stage: build",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run",
                rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        {
            SpanData actualSpanData = spans.breadthFirstStream()
                .filter(sdw -> "Stage: build".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineType = actualSpanData.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            assertThat(actualPipelineType, is("release"));
            String actualBuildTool = actualSpanData.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool, is("maven"));
            SpanData actualSpanData2 = spans.breadthFirstStream()
                .filter(sdw -> "release-script".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineType2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            assertThat(actualPipelineType2, is("release"));
            String actualBuildTool2 = actualSpanData2.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool2, is("maven"));
            SpanData actualSpanData3 = spans.breadthFirstStream()
                .filter(sdw -> rootSpanName.equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineType3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            assertThat(actualPipelineType3, is("release"));
            String actualBuildTool3 = actualSpanData3.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool3, nullValue());
            SpanData actualSpanData4 = spans.breadthFirstStream()
                .filter(sdw -> "Phase: Finalise".equals(sdw.spanData().getName()))
                .findFirst()
                .get()
                .spanData();
            String actualPipelineType4 = actualSpanData4.getAttributes().get(AttributeKey.stringKey("pipeline.type"));
            assertThat(actualPipelineType4, is("release"));
            String actualBuildTool4 = actualSpanData4.getAttributes().get(AttributeKey.stringKey("build.tool"));
            assertThat(actualBuildTool4, nullValue());
        }

        assertThat(spans.cardinality(), is(8L));
    }
}
