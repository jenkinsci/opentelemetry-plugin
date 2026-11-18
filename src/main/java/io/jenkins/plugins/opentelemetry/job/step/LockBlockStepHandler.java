/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;

/**
 * Attributes of the span of a {@code lock} step, and a {@code lock: wait} child span for the time that the build waited
 * for the resource. The wait span is created after the wait, from the times recorded in the pause of the step's flow
 * node, so no span is open while the build waits.
 */
@Extension
public class LockBlockStepHandler implements BlockStepHandler {
    static final String WAIT_SPAN_NAME = "lock: wait";

    /**
     * The step records a pause even when the resource is free, and ends it within the same lock. A pause shorter
     * than this is not a wait.
     */
    static final long MIN_WAIT_MILLIS = 50;

    /**
     * Outer start nodes (run id and node id) whose wait was already handled, until the step ends. An entry stays
     * until the controller restarts if the span of its step is lost before the step ends: it is one short string.
     */
    private final Set<String> waitHandled = ConcurrentHashMap.newKeySet();

    @Inject
    private JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    @Override
    public boolean canHandle(@NonNull String functionName) {
        return "lock".equals(functionName);
    }

    @Override
    public void onBlockStepStart(
            @NonNull Span blockSpan, @NonNull StepStartNode outerStartNode, @NonNull WorkflowRun run) {
        Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(outerStartNode);
        if (arguments.get("resource") instanceof String resource) {
            blockSpan.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_LOCK_RESOURCE, resource);
        }
        if (arguments.get("label") instanceof String label) {
            blockSpan.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_LOCK_LABEL, label);
        }
    }

    @Override
    public void onBlockBodyStart(
            @NonNull Span blockSpan,
            @NonNull StepStartNode outerStartNode,
            @NonNull StepStartNode bodyStartNode,
            @NonNull WorkflowRun run) {
        reportWait(blockSpan, outerStartNode, run);
    }

    @Override
    public void onBlockStepEnd(
            @NonNull Span blockSpan,
            @NonNull StepEndNode outerEndNode,
            @CheckForNull FlowNode nextNode,
            @NonNull WorkflowRun run) {
        StepStartNode outerStartNode = outerEndNode.getStartNode();
        // The body never started: the lock was skipped (no wait), or the acquire timed out or was interrupted
        // (the pause may still be open, then the wait ends with the step)
        if (!isSkipIfLocked(outerStartNode)) {
            reportWait(blockSpan, outerStartNode, run, TimingAction.getStartTime(outerEndNode));
        }
        waitHandled.remove(key(outerStartNode, run));
    }

    /** Reports the wait of a pause that has ended. An open pause is reported when the step ends. */
    private void reportWait(Span lockSpan, StepStartNode outerStartNode, WorkflowRun run) {
        PauseAction pause = firstPause(outerStartNode);
        if (pause != null && !pause.isPaused()) {
            reportWait(lockSpan, outerStartNode, run, pause.getStartTime(), pause.getEndTime());
        }
    }

    /** Reports the wait of the pause, which ends with the step if it is still open. */
    private void reportWait(Span lockSpan, StepStartNode outerStartNode, WorkflowRun run, long stepEndMillis) {
        PauseAction pause = firstPause(outerStartNode);
        if (pause != null) {
            long end = pause.isPaused() ? stepEndMillis : pause.getEndTime();
            reportWait(lockSpan, outerStartNode, run, pause.getStartTime(), end);
        }
    }

    /** @return true if a pause from {@code startMillis} to {@code endMillis} is long enough to be a wait */
    static boolean isWait(long startMillis, long endMillis) {
        return endMillis - startMillis >= MIN_WAIT_MILLIS;
    }

    private void reportWait(Span lockSpan, StepStartNode outerStartNode, WorkflowRun run, long start, long end) {
        long waited = end - start;
        if (!isWait(start, end)) {
            return;
        }
        if (!waitHandled.add(key(outerStartNode, run))) {
            return;
        }
        Span waitSpan = jenkinsControllerOpenTelemetry
                .getDefaultTracer()
                .spanBuilder(WAIT_SPAN_NAME)
                .setParent(Context.current().with(lockSpan))
                .setStartTimestamp(Instant.ofEpochMilli(start))
                .startSpan();
        waitSpan.end(Instant.ofEpochMilli(end));
        lockSpan.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_LOCK_WAIT_DURATION, waited);
    }

    @CheckForNull
    private static PauseAction firstPause(StepStartNode outerStartNode) {
        List<PauseAction> pauses = PauseAction.getPauseActions(outerStartNode);
        return pauses.isEmpty() ? null : pauses.get(0);
    }

    private static boolean isSkipIfLocked(StepStartNode outerStartNode) {
        return Boolean.parseBoolean(String.valueOf(
                ArgumentsAction.getFilteredArguments(outerStartNode).getOrDefault("skipIfLocked", false)));
    }

    private static String key(StepStartNode outerStartNode, WorkflowRun run) {
        return run.getExternalizableId() + "/" + outerStartNode.getId();
    }
}
