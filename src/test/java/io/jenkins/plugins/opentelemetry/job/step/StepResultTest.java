/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.github.rutledgepaulv.prune.Tree;
import hudson.model.Node;
import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.BaseIntegrationTest;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.commons.lang3.SystemUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;

public class StepResultTest extends BaseIntegrationTest {

    @Test
    public void testSimplePipelineWithWithStepResults() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" + "node() {\n"
                + "    stage('build') {\n"
                + "       unstable('stage unstable')\n"
                + "    }\n"
                + "    stage('parallel') {\n"
                + "         catchError(stageResult: 'UNSTABLE') {  // otherwise, the timeout stage would never run\n"
                + "             parallel (\n"
                + "                 first: { xsh (label: 'parallel-first', script: 'exit 1') },\n"
                + "                 second: { xsh (label: 'parallel-second', script: 'exit 0') },\n"
                + "             )\n"
                + "         }\n"
                + "    }\n"
                + "    stage('skipped') {\n"
                + "        org.jenkinsci.plugins.pipeline.modeldefinition.Utils.markStageSkippedForConditional('skipped');\n"
                + "    }\n"
                + "    stage('timeout') {\n"
                + "        timeout(time: 1, unit: 'MILLISECONDS') {\n"
                + "            xsh (label: 'sleep', script: 'sleep 1')\n"
                + "        }\n"
                + "    }\n"
                + "}";
        final Node agent = jenkinsRule.createOnlineSlave();

        final String jobName = "test-simple-pipeline-with-step-results" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.ABORTED, pipeline.scheduleBuild2(0));

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
                spans, "unstable", "Stage: build", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(
                spans,
                "parallel-first",
                "Parallel branch: first",
                "Stage: parallel",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run",
                rootSpanName);
        checkChainOfSpans(
                spans,
                "parallel-second",
                "Parallel branch: second",
                "Stage: parallel",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run",
                rootSpanName);
        checkChainOfSpans(spans, "Stage: skipped", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Stage: timeout", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);

        // Note: pipeline root span is not a step/stage, so it does not get a
        // JenkinsOtelSemanticAttributes.JENKINS_STEP_RESULT attribute (just like it doesn't get any
        // JenkinsOtelSemanticAttributes at all at the moment)
        // Neither are any of the 3 "Phase" spans.

        { // node span: 'ABORTED'  (because timeout stage is aborted)
            SpanData actualSpanData = spans.breadthFirstStream()
                    .filter(sdw -> ExtendedJenkinsAttributes.AGENT_UI.equals(sdw.spanData.getName()))
                    .findFirst()
                    .get()
                    .spanData;
            String actualStageResult =
                    actualSpanData.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_STEP_RESULT);
            MatcherAssert.assertThat(actualStageResult, CoreMatchers.is("ABORTED"));
        }

        { // node allocation span: 'SUCCESS'
            SpanData actualSpanData = spans.breadthFirstStream()
                    .filter(sdw -> ExtendedJenkinsAttributes.AGENT_ALLOCATION_UI.equals(sdw.spanData.getName()))
                    .findFirst()
                    .get()
                    .spanData;
            String actualStageResult =
                    actualSpanData.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_STEP_RESULT);
            MatcherAssert.assertThat(actualStageResult, CoreMatchers.is("SUCCESS"));
        }

        { // stage 'build': 'UNSTABLE'
            SpanData actualSpanData = spans.breadthFirstStream()
                    .filter(sdw -> "Stage: build".equals(sdw.spanData.getName()))
                    .findFirst()
                    .get()
                    .spanData;
            String actualStageResult =
                    actualSpanData.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_STEP_RESULT);
            MatcherAssert.assertThat(actualStageResult, CoreMatchers.is("UNSTABLE"));
        }

        { // stage 'parallel': 'UNSTABLE'  (because catchError caught parallel-first's FAILURE and set stageResult to
            // UNSTABLE)
            SpanData actualSpanData = spans.breadthFirstStream()
                    .filter(sdw -> "Stage: parallel".equals(sdw.spanData.getName()))
                    .findFirst()
                    .get()
                    .spanData;
            String actualStageResult =
                    actualSpanData.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_STEP_RESULT);
            MatcherAssert.assertThat(actualStageResult, CoreMatchers.is("UNSTABLE"));
        }

        { // parallel node 'first': 'FAILURE'
            SpanData actualSpanData = spans.breadthFirstStream()
                    .filter(sdw -> "Parallel branch: first".equals(sdw.spanData.getName()))
                    .findFirst()
                    .get()
                    .spanData;
            String actualStepResult = actualSpanData.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_STEP_RESULT);
            MatcherAssert.assertThat(actualStepResult, CoreMatchers.is("FAILURE"));
        }

        { // xsh node 'parallel-first': 'FAILURE'
            SpanData actualSpanData = spans.breadthFirstStream()
                    .filter(sdw -> "parallel-first".equals(sdw.spanData.getName()))
                    .findFirst()
                    .get()
                    .spanData;
            String actualStepResult = actualSpanData.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_STEP_RESULT);
            MatcherAssert.assertThat(actualStepResult, CoreMatchers.is("FAILURE"));
        }

        { // parallel node 'second': 'SUCCESS'
            SpanData actualSpanData = spans.breadthFirstStream()
                    .filter(sdw -> "Parallel branch: second".equals(sdw.spanData.getName()))
                    .findFirst()
                    .get()
                    .spanData;
            String actualStepResult = actualSpanData.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_STEP_RESULT);
            MatcherAssert.assertThat(actualStepResult, CoreMatchers.is("SUCCESS"));
        }

        { // xsh node 'parallel-second': 'SUCCESS'
            SpanData actualSpanData = spans.breadthFirstStream()
                    .filter(sdw -> "parallel-second".equals(sdw.spanData.getName()))
                    .findFirst()
                    .get()
                    .spanData;
            String actualStepResult = actualSpanData.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_STEP_RESULT);
            MatcherAssert.assertThat(actualStepResult, CoreMatchers.is("SUCCESS"));
        }

        { // stage 'skipped': 'NOT_EXECUTED'
            SpanData actualSpanData = spans.breadthFirstStream()
                    .filter(sdw -> "Stage: skipped".equals(sdw.spanData.getName()))
                    .findFirst()
                    .get()
                    .spanData;
            String actualStageResult =
                    actualSpanData.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_STEP_RESULT);
            MatcherAssert.assertThat(actualStageResult, CoreMatchers.is("NOT_EXECUTED"));
        }

        { // stage 'timeout': 'ABORTED'
            SpanData actualSpanData = spans.breadthFirstStream()
                    .filter(sdw -> "Stage: timeout".equals(sdw.spanData.getName()))
                    .findFirst()
                    .get()
                    .spanData;
            String actualStageResult =
                    actualSpanData.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_STEP_RESULT);
            MatcherAssert.assertThat(actualStageResult, CoreMatchers.is("ABORTED"));
        }

        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(15L));
    }
}
