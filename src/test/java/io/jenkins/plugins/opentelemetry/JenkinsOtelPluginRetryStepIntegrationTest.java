/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rutledgepaulv.prune.Tree;
import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.workflow.actions.BodyInvocationAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;

/**
 * A {@code retry} step listed in the {@code blockSteps} setting gets a span, and each attempt of its body gets a
 * {@code retry: attempt N} span under it.
 */
public class JenkinsOtelPluginRetryStepIntegrationTest extends BaseIntegrationTest {

    private static final AttributeKey<Long> ATTEMPT = AttributeKey.longKey("jenkins.pipeline.step.retry.attempt");
    private static final AttributeKey<Long> MAX_ATTEMPTS =
            AttributeKey.longKey("jenkins.pipeline.step.retry.max_attempts");
    private static final String ATTEMPT_LIMIT_PROPERTY =
            "io.jenkins.plugins.opentelemetry.job.step.RetryBlockStepHandler.attemptLimit";

    /** Number of spans of a pipeline with one stage on an agent and one shell step. */
    private static final int ONE_SHELL_SPAN_COUNT = 8;

    /** A pipeline whose retry body runs a shell step labelled with the attempt, then fails until the given attempt. */
    private static String retryPipeline(int count, int succeedAt) {
        return """
                def attempt = 0
                node() {
                    stage('ze-stage') {
                        retry(%d) {
                            attempt++
                            if (isUnix()) { sh label: 'ze-shell-' + attempt, script: 'echo ze-echo' }
                            else { bat label: 'ze-shell-' + attempt, script: 'echo ze-echo' }
                            if (attempt < %d) {
                                error 'ze-fail-' + attempt
                            }
                        }
                    }
                }
                """.formatted(count, succeedAt);
    }

    @Test
    public void testNothingChangesWhenBlockStepsIsEmpty() throws Exception {
        runPipeline(retryPipeline(3, 3), Result.SUCCESS);

        getBuildTrace();
        assertNoRetrySpans();
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertEquals(
                    "Stage: ze-stage", parentOf(onlySpan("ze-shell-" + attempt)).getName());
        }
        // The one-shell pipeline, plus two more shell steps and two error steps
        assertEquals(ONE_SHELL_SPAN_COUNT + 4, exportedSpans().size());
    }

    @Test
    public void testNoRetrySpansWhenRetryIsNotInBlockSteps() throws Exception {
        configureBlockSteps("timeout,withEnv");
        runPipeline(retryPipeline(3, 3), Result.SUCCESS);

        getBuildTrace();
        assertNoRetrySpans();
        assertEquals("Stage: ze-stage", parentOf(onlySpan("ze-shell-2")).getName());
    }

    @Test
    public void testRetryOfThreeWhoseBodyFailsTwice() throws Exception {
        configureBlockSteps("retry");
        runPipeline(retryPipeline(3, 3), Result.SUCCESS);

        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpansUpToRunPhase(
                spans,
                "ze-shell-3",
                "retry: attempt 3",
                "retry",
                "Stage: ze-stage",
                ExtendedJenkinsAttributes.AGENT_UI);

        SpanData retry = onlySpan("retry");
        assertEquals(3L, retry.getAttributes().get(MAX_ATTEMPTS));
        assertEquals(StatusCode.OK, retry.getStatus().getStatusCode());
        assertEquals("Stage: ze-stage", parentOf(retry).getName());

        List<SpanData> attempts = attemptSpans();
        assertEquals(List.of("retry: attempt 1", "retry: attempt 2", "retry: attempt 3"), names(attempts));
        for (int i = 0; i < 3; i++) {
            SpanData attempt = attempts.get(i);
            assertEquals(i + 1L, attempt.getAttributes().get(ATTEMPT));
            assertEquals(retry.getSpanId(), attempt.getParentSpanId(), "parent of " + attempt.getName());
            // The steps of each attempt are under the span of that attempt, the later ones too
            assertEquals(attempt.getSpanId(), onlySpan("ze-shell-" + (i + 1)).getParentSpanId());
        }
        // The error action of the attempt's end node gives the status of the attempt span
        assertEquals(StatusCode.ERROR, attempts.get(0).getStatus().getStatusCode());
        assertEquals("ze-fail-1", attempts.get(0).getStatus().getDescription());
        assertEquals(StatusCode.ERROR, attempts.get(1).getStatus().getStatusCode());
        assertEquals("ze-fail-2", attempts.get(1).getStatus().getDescription());
        assertEquals(StatusCode.OK, attempts.get(2).getStatus().getStatusCode());
        // The attempts follow each other, inside the span of the step
        assertTrue(attempts.get(0).getEndEpochNanos() <= attempts.get(1).getStartEpochNanos());
        assertTrue(attempts.get(2).getEndEpochNanos() <= retry.getEndEpochNanos());
        assertEquals(ONE_SHELL_SPAN_COUNT + 4 + 1 + 3, exportedSpans().size());
    }

    @Test
    public void testBodyThatSucceedsAtOnceHasOneAttempt() throws Exception {
        configureBlockSteps("retry");
        runPipeline(retryPipeline(3, 1), Result.SUCCESS);

        getBuildTrace();
        List<SpanData> attempts = attemptSpans();
        assertEquals(List.of("retry: attempt 1"), names(attempts));
        assertEquals(StatusCode.OK, attempts.get(0).getStatus().getStatusCode());
        assertEquals(StatusCode.OK, onlySpan("retry").getStatus().getStatusCode());
    }

    @Test
    public void testRetryThatRunsOutOfAttemptsEndsWithAnError() throws Exception {
        configureBlockSteps("retry");
        runPipeline(retryPipeline(2, 99), Result.FAILURE);

        getBuildTrace();
        List<SpanData> attempts = attemptSpans();
        assertEquals(List.of("retry: attempt 1", "retry: attempt 2"), names(attempts));
        assertEquals(StatusCode.ERROR, attempts.get(1).getStatus().getStatusCode());
        assertEquals("ze-fail-2", attempts.get(1).getStatus().getDescription());
        SpanData retry = onlySpan("retry");
        assertEquals(2L, retry.getAttributes().get(MAX_ATTEMPTS));
        assertEquals(StatusCode.ERROR, retry.getStatus().getStatusCode());
        assertEquals("ze-fail-2", retry.getStatus().getDescription());
    }

    @Test
    public void testAttemptsBeyondTheLimitHaveNoSpan() throws Exception {
        configureBlockSteps("retry");
        System.setProperty(ATTEMPT_LIMIT_PROPERTY, "3");
        try {
            runPipeline(retryPipeline(8, 8), Result.SUCCESS);
        } finally {
            System.clearProperty(ATTEMPT_LIMIT_PROPERTY);
        }

        getBuildTrace();
        List<SpanData> attempts = attemptSpans();
        assertEquals(List.of("retry: attempt 1", "retry: attempt 2", "retry: attempt 3"), names(attempts));
        for (int i = 0; i < 3; i++) {
            assertEquals(i + 1L, attempts.get(i).getAttributes().get(ATTEMPT));
            assertEquals(StatusCode.ERROR, attempts.get(i).getStatus().getStatusCode());
            assertEquals(
                    attempts.get(i).getSpanId(), onlySpan("ze-shell-" + (i + 1)).getParentSpanId());
        }
        // Beyond the limit, the steps of an attempt are under the span of the step
        SpanData retry = onlySpan("retry");
        for (int attempt = 4; attempt <= 8; attempt++) {
            assertEquals(retry.getSpanId(), onlySpan("ze-shell-" + attempt).getParentSpanId());
        }
        assertEquals(8L, retry.getAttributes().get(MAX_ATTEMPTS));
        assertEquals(StatusCode.OK, retry.getStatus().getStatusCode());
    }

    private void configureBlockSteps(String blockSteps) {
        JenkinsOpenTelemetryPluginConfiguration.get().setBlockSteps(blockSteps);
    }

    private WorkflowRun runPipeline(String script, Result expected) throws Exception {
        jenkinsRule.createOnlineSlave();
        WorkflowJob job =
                jenkinsRule.createProject(WorkflowJob.class, "test-retry-step-" + jobNameSuffix.incrementAndGet());
        job.setDefinition(new CpsFlowDefinition(script, true));
        return jenkinsRule.assertBuildStatus(expected, job.scheduleBuild2(0));
    }

    private static List<StepStartNode> retryBodyStartNodes(WorkflowRun run) {
        return new DepthFirstScanner()
                .allNodes(run.getExecution().getCurrentHeads()).stream()
                        .filter(node -> node instanceof StepStartNode start
                                && start.getAction(BodyInvocationAction.class) != null
                                && start.getDescriptor() != null
                                && "retry".equals(start.getDescriptor().getFunctionName()))
                        .map(StepStartNode.class::cast)
                        .sorted(Comparator.comparingInt((FlowNode node) -> Integer.parseInt(node.getId())))
                        .collect(Collectors.toList());
    }

    private void assertNoRetrySpans() {
        List<String> retrySpans = exportedSpans().stream()
                .map(SpanData::getName)
                .filter(name -> name.startsWith("retry"))
                .collect(Collectors.toList());
        assertEquals(List.of(), retrySpans, "retry spans");
    }

    private List<SpanData> exportedSpans() {
        return InMemorySpanExporterProvider.LAST_CREATED_INSTANCE.getFinishedSpanItems();
    }

    private List<SpanData> attemptSpans() {
        return exportedSpans().stream()
                .filter(span -> span.getName().startsWith("retry: attempt "))
                .sorted(Comparator.comparingLong(SpanData::getStartEpochNanos))
                .collect(Collectors.toList());
    }

    private static List<String> names(List<SpanData> spans) {
        return spans.stream().map(SpanData::getName).collect(Collectors.toList());
    }

    private SpanData onlySpan(String name) {
        List<SpanData> found =
                exportedSpans().stream().filter(s -> name.equals(s.getName())).collect(Collectors.toList());
        assertEquals(1, found.size(), "spans named '" + name + "'");
        return found.get(0);
    }

    private SpanData parentOf(SpanData span) {
        return exportedSpans().stream()
                .filter(s -> s.getSpanId().equals(span.getParentSpanId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no parent of " + span.getName()));
    }
}
