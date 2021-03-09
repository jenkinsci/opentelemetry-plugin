/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.Nonnull;

public interface StepHandler {
    boolean canCreateSpanBuilder(@Nonnull FlowNode flowNode);

    @Nonnull
    SpanBuilder createSpanBuilder(@Nonnull FlowNode node, @Nonnull Tracer tracer) throws Exception ;
}