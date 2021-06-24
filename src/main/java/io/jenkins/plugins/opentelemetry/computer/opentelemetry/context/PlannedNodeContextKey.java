/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer.opentelemetry.context;

import hudson.slaves.NodeProvisioner;
import io.opentelemetry.context.ContextKey;

import javax.annotation.concurrent.Immutable;

/**
 * See {@code io.opentelemetry.api.trace.SpanContextKey}
 */
@Immutable
public final class PlannedNodeContextKey {
    public static final ContextKey<NodeProvisioner.PlannedNode> KEY = ContextKey.named(NodeProvisioner.PlannedNode.class.getName());

    private PlannedNodeContextKey(){}
}
