/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.BodyInvocationAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/** A span per attempt of a {@code retry} step, under the span of the step. */
@Extension
public class RetryBlockStepHandler implements BlockStepHandler {
    private static final Logger LOGGER = Logger.getLogger(RetryBlockStepHandler.class.getName());

    /**
     * Attempts after this number get no span, to bound the walk of the flow graph that numbers them. Attempt N reads
     * N - 1 earlier body end nodes, so the walk reads at most this many nodes.
     */
    private static final String ATTEMPT_LIMIT_PROPERTY = RetryBlockStepHandler.class.getName() + ".attemptLimit";

    private static final int DEFAULT_ATTEMPT_LIMIT = 100;

    @Override
    public boolean canHandle(@NonNull String functionName) {
        return "retry".equals(functionName);
    }

    @Override
    public void onBlockStepStart(
            @NonNull Span blockSpan, @NonNull StepStartNode outerStartNode, @NonNull WorkflowRun run) {
        if (ArgumentsAction.getFilteredArguments(outerStartNode).get("count") instanceof Number count) {
            blockSpan.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_RETRY_MAX_ATTEMPTS, count.longValue());
        }
    }

    @Override
    @NonNull
    public Optional<BodySpan> bodySpan(@NonNull StepStartNode outerStartNode, @NonNull StepStartNode bodyStartNode) {
        int attempt = attemptNumber(
                bodyStartNode, SystemProperties.getInteger(ATTEMPT_LIMIT_PROPERTY, DEFAULT_ATTEMPT_LIMIT));
        if (attempt < 1) {
            LOGGER.log(
                    Level.FINE,
                    () -> "No span for the retry attempt of node " + bodyStartNode.getId()
                            + ", beyond the attempt limit");
            return Optional.empty();
        }
        return Optional.of(new BodySpan(
                "retry: attempt " + attempt,
                Attributes.of(ExtendedJenkinsAttributes.JENKINS_STEP_RETRY_ATTEMPT, (long) attempt)));
    }

    /**
     * Number of the attempt that the body start node starts: one more than the body end nodes of the earlier attempts,
     * found by walking back from the node to the outer start node of the step. The flow graph is the only source, so
     * no state is kept in memory.
     *
     * @param limit the highest attempt number that is counted. The walk reads one node per earlier attempt and stops
     *              at the node that would make the number higher than the limit, so it reads at most this many
     *              nodes
     * @return the attempt number, from 1, or -1 if it is higher than the limit or the graph has no outer start node
     */
    public static int attemptNumber(@NonNull StepStartNode bodyStartNode, int limit) {
        int earlierAttempts = 0;
        int steps = 0;
        FlowNode node = firstParent(bodyStartNode);
        // The outer start node is the first block start node met when the blocks on the way are stepped over
        while (node != null && !(node instanceof BlockStartNode)) {
            if (++steps >= limit) {
                return -1;
            }
            if (node instanceof BlockEndNode<?> endNode) {
                if (endNode.getAction(BodyInvocationAction.class) != null) {
                    earlierAttempts++;
                }
                node = firstParent(endNode.getStartNode());
            } else {
                node = firstParent(node);
            }
        }
        return node == null ? -1 : earlierAttempts + 1;
    }

    @CheckForNull
    private static FlowNode firstParent(@NonNull FlowNode node) {
        List<FlowNode> parents = node.getParents();
        return parents.isEmpty() ? null : parents.get(0);
    }
}
