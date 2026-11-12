/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.Span;
import java.util.Map;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;

/**
 * Attributes of the span of a {@code timeout} step: its arguments, and whether the timeout fired.
 */
@Extension
public class TimeoutBlockStepHandler implements BlockStepHandler {
    private static final String EXCEEDED_TIMEOUT_CAUSE_SUFFIX = "TimeoutStepExecution$ExceededTimeout";

    @Override
    public boolean canHandle(@NonNull String functionName) {
        return "timeout".equals(functionName);
    }

    @Override
    public void onBlockStepStart(
            @NonNull Span blockSpan, @NonNull StepStartNode outerStartNode, @NonNull WorkflowRun run) {
        Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(outerStartNode);
        if (arguments.get("time") instanceof Number time) {
            blockSpan.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_TIMEOUT_TIME, time.longValue());
        }
        // The defaults of the step when the argument is not given
        blockSpan.setAttribute(
                ExtendedJenkinsAttributes.JENKINS_STEP_TIMEOUT_UNIT,
                String.valueOf(arguments.getOrDefault("unit", "MINUTES")));
        blockSpan.setAttribute(
                ExtendedJenkinsAttributes.JENKINS_STEP_TIMEOUT_ACTIVITY,
                Boolean.parseBoolean(String.valueOf(arguments.getOrDefault("activity", false))));
    }

    @Override
    public void onBlockStepEnd(
            @NonNull Span blockSpan,
            @NonNull StepEndNode outerEndNode,
            @CheckForNull FlowNode nextNode,
            @NonNull WorkflowRun run) {
        blockSpan.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_TIMEOUT_EXCEEDED, isExceeded(outerEndNode));
    }

    private static boolean isExceeded(StepEndNode outerEndNode) {
        ErrorAction errorAction = outerEndNode.getError();
        return errorAction != null
                && errorAction.getError() instanceof FlowInterruptedException interrupted
                && interrupted.getCauses().stream()
                        .anyMatch(cause -> cause.getClass().getName().endsWith(EXCEEDED_TIMEOUT_CAUSE_SUFFIX));
    }
}
