/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;

public interface StepHandler extends Comparable<StepHandler> {
    boolean canCreateSpanBuilder(@Nonnull FlowNode flowNode, @Nonnull WorkflowRun run);

    @Nonnull
    SpanBuilder createSpanBuilder(@Nonnull FlowNode node, @Nonnull WorkflowRun run, @Nonnull Tracer tracer);

    /**
     * @return the ordinal of this handler to execute step handlers in predictable order. The smallest ordinal is executed first.
     */
    default int ordinal() {
        return 0;
    }

    @Override
    default int compareTo(StepHandler other) {
        if (this.ordinal() == other.ordinal()) {
            return this.getClass().getName().compareTo(other.getClass().getName());
        } else {
            return Integer.compare(this.ordinal(), other.ordinal());
        }
    }

    /**
     * Invoked after the {@link io.opentelemetry.api.trace.Span} has been created using the {@link SpanBuilder} created by
     * {@link #createSpanBuilder(FlowNode, WorkflowRun, Tracer)}.
     *
     * The created {@link io.opentelemetry.api.trace.Span} can be retrieved using {@link io.opentelemetry.api.trace.Span#current()}
     */
    default void afterSpanCreated(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {}
}