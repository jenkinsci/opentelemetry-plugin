/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import io.opentelemetry.api.trace.SpanBuilder;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.Nonnull;

public interface StepHandler {
    boolean canHandle(@Nonnull FlowNode flowNode);

    void handle(@Nonnull FlowNode node, @Nonnull SpanBuilder spanBuilder) throws Exception ;
}
