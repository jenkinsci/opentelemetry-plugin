/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Behavior that is specific to one block step (a step with a body, for example {@code timeout} or {@code withEnv})
 * that the user listed in the "block steps" setting. The plugin creates the span of the block step and the spans of
 * the body that the handlers ask for ({@link #bodySpan(StepStartNode, StepStartNode)}), ends them, and calls the
 * handlers whose {@link #canHandle(String)} accepts the step's function name. Handlers do not end these spans: the
 * plugin ends them all in one way, which keeps credential IDs out of their error unless the setting allows them.
 * <p>
 * The callbacks run inside the synchronous graph listener: read flow node actions and set attributes, do no I/O.
 * An exception thrown by a callback is logged and does not change the span, the build or the other handlers.
 */
@Restricted(Beta.class)
public interface BlockStepHandler extends ExtensionPoint, Comparable<BlockStepHandler> {

    /**
     * @return all the handlers, in the order of {@link #ordinal()}, then of the class name
     */
    @NonNull
    static List<BlockStepHandler> all() {
        List<BlockStepHandler> handlers = new ArrayList<>(ExtensionList.lookup(BlockStepHandler.class));
        Collections.sort(handlers);
        return handlers;
    }

    /**
     * @param functionName the function name of the step, for example {@code timeout}
     * @return true if this handler adds its behavior to the block span of the step with this name
     */
    boolean canHandle(@NonNull String functionName);

    /**
     * @return the position of this handler when several handle the same step. The smallest ordinal is called first.
     */
    default int ordinal() {
        return 100;
    }

    @Override
    default int compareTo(BlockStepHandler other) {
        if (this.ordinal() == other.ordinal()) {
            return this.getClass().getName().compareTo(other.getClass().getName());
        }
        return Integer.compare(this.ordinal(), other.ordinal());
    }

    /**
     * After the span of the block step has started on its outer start node, before the body starts. Add the
     * attributes of the step here.
     *
     * @param blockSpan      the span of the block step
     * @param outerStartNode the outer start node of the step, with its arguments
     * @param run            the run of the node
     */
    default void onBlockStepStart(
            @NonNull Span blockSpan, @NonNull StepStartNode outerStartNode, @NonNull WorkflowRun run) {}

    /**
     * When the body of the block step starts, once per invocation of the body: the span that the plugin gives this
     * invocation of the body, if any. The plugin starts it as a child of the span of the block step and ends it with
     * the body end node, with the status and error of the body, in the same way as the span of the block step.
     *
     * @param outerStartNode the outer start node of the step, with its arguments
     * @param bodyStartNode  the body start node
     * @return the name and attributes of the span of this invocation of the body, or empty for no span
     */
    @NonNull
    default Optional<BodySpan> bodySpan(@NonNull StepStartNode outerStartNode, @NonNull StepStartNode bodyStartNode) {
        return Optional.empty();
    }

    /**
     * After the body of the block step started, on the body start node. Called once per invocation of the body,
     * after the span of {@link #bodySpan(StepStartNode, StepStartNode)} started.
     *
     * @param blockSpan      the span of the block step
     * @param outerStartNode the outer start node of the step, with its arguments
     * @param bodyStartNode  the body start node
     * @param run            the run of the node
     */
    default void onBlockBodyStart(
            @NonNull Span blockSpan,
            @NonNull StepStartNode outerStartNode,
            @NonNull StepStartNode bodyStartNode,
            @NonNull WorkflowRun run) {}

    /**
     * When the next node arrives after the body end node, so its error is known. Called once per invocation of the
     * body.
     *
     * @param bodyEndNode the body end node
     * @param nextNode    the node that arrived after it, may be {@code null}
     * @param run         the run of the node
     */
    default void onBlockBodyEnd(
            @NonNull StepEndNode bodyEndNode, @CheckForNull FlowNode nextNode, @NonNull WorkflowRun run) {}

    /**
     * When the next node arrives after the outer end node (the error of the step is known), just before the span of
     * the block step ends with the time of the end node.
     *
     * @param blockSpan    the span of the block step
     * @param outerEndNode the outer end node of the step, with its error if any
     * @param nextNode     the node that arrived after it, may be {@code null}
     * @param run          the run of the node
     */
    default void onBlockStepEnd(
            @NonNull Span blockSpan,
            @NonNull StepEndNode outerEndNode,
            @CheckForNull FlowNode nextNode,
            @NonNull WorkflowRun run) {}

    /**
     * A span for one invocation of the body of a block step.
     *
     * @param name       the name of the span
     * @param attributes the attributes of the span
     */
    record BodySpan(@NonNull String name, @NonNull Attributes attributes) {}
}
