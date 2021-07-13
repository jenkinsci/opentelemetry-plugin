/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer.opentelemetry;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.computer.OtelTraceService;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link CloudProvisioningListener} that setups the OpenTelemetry {@link io.opentelemetry.context.Context}
 * with the current {@link Span}.
 */
public abstract class OtelContextAwareAbstractCloudProvisioningListener extends CloudProvisioningListener {

    private final static Logger LOGGER = Logger.getLogger(OtelContextAwareAbstractCloudProvisioningListener.class.getName());

    private OtelTraceService otelTraceService;
    private Tracer tracer;
    private Meter meter;

    @Inject
    public final void setOpenTelemetryTracerService(@Nonnull OtelTraceService otelTraceService, @Nonnull OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        LOGGER.log(Level.FINE, () -> "setOpenTelemetryTracerService()");
        this.otelTraceService = otelTraceService;
        this.tracer = this.otelTraceService.getTracer();
        this.meter = openTelemetrySdkProvider.getMeter();
    }

    @Override
    public void onStarted(Cloud cloud, Label label, Collection<NodeProvisioner.PlannedNode> plannedNodes) {
        LOGGER.log(Level.FINE, () -> "onStarted(" + label.getExpression() + ")");
        this._onStarted(cloud, label, plannedNodes);
    }

    public void _onStarted(Cloud cloud, Label label, Collection<NodeProvisioner.PlannedNode> plannedNodes) {
    }

    @Override
    public void onComplete(NodeProvisioner.PlannedNode plannedNode, Node node) {
        LOGGER.log(Level.FINE, () -> "onComplete(" + node + ")");
        try (Scope scope = getTraceService().setupContext(plannedNode)) {
            this._onComplete(plannedNode, node);
        }
    }

    public void _onComplete(NodeProvisioner.PlannedNode plannedNode, Node node) {
    }

    @Override
    public void onCommit(@NonNull NodeProvisioner.PlannedNode plannedNode, @NonNull Node node) {
        LOGGER.log(Level.FINE, () -> "onCommit(" + node + ")");
        try (Scope scope = getTraceService().setupContext(plannedNode)) {
            this._onCommit(plannedNode, node);
        }
    }

    public void _onCommit(@NonNull NodeProvisioner.PlannedNode plannedNode, @NonNull Node node) {
    }

    @Override
    public void onFailure(NodeProvisioner.PlannedNode plannedNode, Throwable t) {
        LOGGER.log(Level.FINE, () -> "onFailure(" + plannedNode + ")");
        try (Scope scope = getTraceService().setupContext(plannedNode)) {
            this._onFailure(plannedNode, t);
        }
    }

    public void _onFailure(NodeProvisioner.PlannedNode plannedNode, Throwable t) {
    }

    @Override
    public void onRollback(@NonNull NodeProvisioner.PlannedNode plannedNode, @NonNull Node node,
                           @NonNull Throwable t) {
        LOGGER.log(Level.FINE, () -> "onRollback(" + node + ")");
        try (Scope scope = getTraceService().setupContext(plannedNode)) {
            this._onRollback(plannedNode, node, t);
        }
    }

    public void _onRollback(@NonNull NodeProvisioner.PlannedNode plannedNode, @NonNull Node node,
                            @NonNull Throwable t) {
    }

    public OtelTraceService getTraceService() {
        return otelTraceService;
    }

    public Tracer getTracer() {
        return tracer;
    }

    public Meter getMeter() {
        return meter;
    }
}
