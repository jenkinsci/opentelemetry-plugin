/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer.opentelemetry.context;

import io.opentelemetry.context.ContextKey;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.concurrent.Immutable;

/**
 * See {@code io.opentelemetry.api.trace.SpanContextKey}
 */
@Immutable
public final class FlowNodeContextKey {
    public static final ContextKey<FlowNode> KEY = ContextKey.named(FlowNodeContextKey.class.getName());

    private FlowNodeContextKey(){}
}
