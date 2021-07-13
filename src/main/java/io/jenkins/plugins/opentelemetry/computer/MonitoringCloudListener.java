/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import com.google.errorprone.annotations.MustBeClosed;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.computer.opentelemetry.OtelContextAwareAbstractCloudProvisioningListener;
import io.jenkins.plugins.opentelemetry.computer.opentelemetry.context.PlannedNodeContextKey;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Verify.verifyNotNull;

@Extension
public class MonitoringCloudListener extends OtelContextAwareAbstractCloudProvisioningListener {
    private final static Logger LOGGER = Logger.getLogger(MonitoringCloudListener.class.getName());

    private LongCounter failureCloudCounter;
    private LongCounter totalCloudCount;

    private CloudSpanNamingStrategy cloudSpanNamingStrategy;

    @PostConstruct
    public void postConstruct() {
        failureCloudCounter = getMeter().longCounterBuilder(JenkinsSemanticMetrics.JENKINS_CLOUD_AGENTS_FAILURE)
            .setDescription("Number of failed cloud agents when provisioning")
            .setUnit("1")
            .build();
        totalCloudCount = getMeter().longCounterBuilder(JenkinsSemanticMetrics.JENKINS_CLOUD_AGENTS_COMPLETED)
            .setDescription("Number of provisioned cloud agents")
            .setUnit("1")
            .build();
    }

    @Override
    public void _onStarted(Cloud cloud, Label label, Collection<NodeProvisioner.PlannedNode> plannedNodes) {
        LOGGER.log(Level.FINE, () -> "_onStarted(" + label + ")");
        for (NodeProvisioner.PlannedNode plannedNode  : plannedNodes) {
            _onStarted(cloud, label, plannedNode);
        }
    }

    public void _onStarted(Cloud cloud, Label label, NodeProvisioner.PlannedNode plannedNode) {
        LOGGER.log(Level.FINE, () -> "_onStarted(label: " + label + ", label.nodes: " + label.getNodes().toString() + ", plannedNode: " + plannedNode + ")");

        // Span name format: "(<cloud>-)?<template-name>-{id}"
        //    cloud is optional
        //    template-name is defined in the cloud configuration
        //    {id} is the low cardinality
        String rootSpanName = getCloudNamePrefix(cloud) + this.cloudSpanNamingStrategy.getRootSpanName(plannedNode);
        JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault("cloud", cloud.getDescriptor());
        SpanBuilder rootSpanBuilder = getTracer().spanBuilder(rootSpanName).setSpanKind(SpanKind.SERVER);

        // TODO move this to a pluggable span enrichment API with implementations for different observability backends
        // Regarding the value `unknown`, see https://github.com/jenkinsci/opentelemetry-plugin/issues/51
        rootSpanBuilder
            .setAttribute(JenkinsOtelSemanticAttributes.ELASTIC_TRANSACTION_TYPE, "unknown")
            .setAttribute(JenkinsOtelSemanticAttributes.CI_CLOUD_NAME, plannedNode.displayName)
            .setAttribute(JenkinsOtelSemanticAttributes.CI_CLOUD_LABEL, label.getExpression())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());

        // ENRICH attributes with every Cloud specifics
        for (CloudHandler cloudHandler : ExtensionList.lookup(CloudHandler.class)) {
            if (cloudHandler.canAddAttributes(cloud)) {
                try {
                    cloudHandler.addCloudAttributes(cloud, rootSpanBuilder);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, cloud.name + " failure to handle cloud provider with handler " + cloudHandler, e);
                }
                break;
            }
        }

        // START root span
        Span rootSpan = rootSpanBuilder.startSpan();
        this.getTraceService().putSpan(plannedNode, rootSpan);
        try (final Scope rootSpanScope = rootSpan.makeCurrent()) {
            LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - begin root " + OtelUtils.toDebugString(rootSpan));

            // START started span
            Span startSpan = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.CLOUD_SPAN_PHASE_STARTED_NAME)
                .setParent(Context.current().with(rootSpan))
                .startSpan();
            LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - begin " + OtelUtils.toDebugString(startSpan));

            this.getTraceService().putSpan(plannedNode, startSpan);
            startSpan.makeCurrent();
        }
    }

    @Override
    public void _onCommit(@NonNull NodeProvisioner.PlannedNode plannedNode, @NonNull Node node) {
        LOGGER.log(Level.FINE, () -> "_onCommit(plannedNode: " + plannedNode + "node: " + node + ")");
        try (Scope parentScope = endCloudPhaseSpan(plannedNode)) {
            Span span = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.CLOUD_SPAN_PHASE_COMMIT_NAME).setParent(Context.current()).startSpan();
            addCloudSpanAttributes(node, span);
            span.setStatus(StatusCode.OK);
            span.end();
            LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - end _onCommit " + OtelUtils.toDebugString(span));
        }
    }

    @Override
    public void _onFailure(NodeProvisioner.PlannedNode plannedNode, Throwable t) {
        LOGGER.log(Level.FINE, () -> "_onFailure(plannedNode: " + plannedNode + ")");
        failureCloudCounter.add(1);
        try (Scope parentScope = endCloudPhaseSpan(plannedNode)) {
            Span span = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.CLOUD_SPAN_PHASE_FAILURE_NAME).setParent(Context.current()).startSpan();
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getMessage());
            span.end();
            LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - end _onFailure " + OtelUtils.toDebugString(span));
        }
    }

    @Override
    public void _onRollback(@NonNull NodeProvisioner.PlannedNode plannedNode, @NonNull Node node,
                            @NonNull Throwable t){
        LOGGER.log(Level.FINE, () -> "_onRollback(plannedNode" + plannedNode + ", node: " + node + ")");
        failureCloudCounter.add(1);
        try (Scope parentScope = endCloudPhaseSpan(plannedNode)) {
            Span span = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.CLOUD_SPAN_PHASE_FAILURE_NAME).setParent(Context.current()).startSpan();
            addCloudSpanAttributes(node, span);
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getMessage());
            span.end();
            LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - end _onRollback " + OtelUtils.toDebugString(span));
        }
    }

    @Override
    public void _onComplete(NodeProvisioner.PlannedNode plannedNode, Node node) {
        LOGGER.log(Level.FINE, () -> "_onComplete(plannedNode: " + plannedNode + ", node: " + node + ")");
        totalCloudCount.add(1);
        try (Scope parentScope = endCloudPhaseSpan(plannedNode)) {
            Span span = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.CLOUD_SPAN_PHASE_COMPLETE_NAME).setParent(Context.current()).startSpan();
            addCloudSpanAttributes(node, span);
            span.setStatus(StatusCode.OK);
            span.end();
            LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - end _onComplete " + OtelUtils.toDebugString(span));
        }
    }

    @MustBeClosed
    @Nonnull
    protected Scope endCloudPhaseSpan(@NonNull NodeProvisioner.PlannedNode plannedNode) {
        Span cloudPhaseSpan = verifyNotNull(Span.current(), "No cloudPhaseSpan found in context");
        cloudPhaseSpan.end();
        LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - end " + OtelUtils.toDebugString(cloudPhaseSpan));

        Span newCurrentSpan = this.getTraceService().getSpan(plannedNode);
        Scope newScope = newCurrentSpan.makeCurrent();
        Context.current().with(PlannedNodeContextKey.KEY, plannedNode);
        return newScope;
    }

    private void addCloudSpanAttributes(@NonNull Node node, @NonNull Span span) {
        // ENRICH attributes with every Cloud Node specifics
        for (CloudHandler cloudHandler : ExtensionList.lookup(CloudHandler.class)) {
            if (cloudHandler.canAddAttributes(node)) {
                try {
                    cloudHandler.addCloudSpanAttributes(node, span);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, node.getNodeName() + " failure to handle node provider with handler " + cloudHandler, e);
                }
                break;
            }
        }
    }

    private String getCloudNamePrefix(@NonNull Cloud cloud) {
        for (CloudHandler cloudHandler : ExtensionList.lookup(CloudHandler.class)) {
            if (cloudHandler.canAddAttributes(cloud)) {
                return cloudHandler.getCloudName() + "-";
            }
        }
        return "";
    }

    @Inject
    public void setCloudSpanNamingStrategy(CloudSpanNamingStrategy cloudSpanNamingStrategy) {
        this.cloudSpanNamingStrategy = cloudSpanNamingStrategy;
    }
}
