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
        LOGGER.log(Level.FINE, () -> "_onStarted(label: " + label + ")");
        LOGGER.log(Level.FINEST, () -> "_onStarted(label.nodes: " + label.getNodes().toString() + ")");
        LOGGER.log(Level.FINEST, () -> "_onStarted(plannedNode: " + plannedNode.toString() + ")");

        String rootSpanName = this.cloudSpanNamingStrategy.getRootSpanName(plannedNode);
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
                    cloudHandler.addCloudAttributes(cloud, label, rootSpanBuilder);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, cloud.name + " failure to handle cloud provider with handler " + cloudHandler, e);
                }
                break;
            }
        }

        // START ROOT SPAN
        Span rootSpan = rootSpanBuilder.startSpan();
        this.getTraceService().putSpan(plannedNode, rootSpan);
        rootSpan.makeCurrent();
        LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - begin root " + OtelUtils.toDebugString(rootSpan));
    }

    @Override
    public void _onCommit(@NonNull NodeProvisioner.PlannedNode plannedNode, @NonNull Node node) {
        LOGGER.log(Level.FINE, () -> "_onCommit(node: " + node + ")");
        LOGGER.log(Level.FINEST, () -> "_onCommit(plannedNode: " + plannedNode.toString() + ")");
        try (Scope parentScope = endCloudPhaseSpan(plannedNode)) {
            Span span = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.CLOUD_SPAN_PHASE_COMMIT_NAME).setParent(Context.current()).startSpan();
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
        LOGGER.log(Level.FINE, () -> "_onRollback(plannedNode" + plannedNode + ")");
        LOGGER.log(Level.FINEST, () -> "_onRollback(node: " + node.toString() + ")");
        failureCloudCounter.add(1);
        try (Scope parentScope = endCloudPhaseSpan(plannedNode)) {
            Span span = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.CLOUD_SPAN_PHASE_FAILURE_NAME).setParent(Context.current()).startSpan();
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getMessage());
            span.end();
            LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - end _onRollback " + OtelUtils.toDebugString(span));
        }
    }

    @Override
    public void _onComplete(NodeProvisioner.PlannedNode plannedNode, Node node) {
        LOGGER.log(Level.FINE, () -> "_onComplete(plannedNode: " + plannedNode + ")");
        LOGGER.log(Level.FINEST, () -> "_onComplete(node: " + node.toString() + ")");
        totalCloudCount.add(1);
        try (Scope parentScope = endCloudPhaseSpan(plannedNode)) {
            Span span = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.CLOUD_SPAN_PHASE_COMPLETE_NAME).setParent(Context.current()).startSpan();
            span.setStatus(StatusCode.OK);
            span.end();
            LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - begin _onComplete " + OtelUtils.toDebugString(span));
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

    @Inject
    public void setCloudSpanNamingStrategy(CloudSpanNamingStrategy cloudSpanNamingStrategy) {
        this.cloudSpanNamingStrategy = cloudSpanNamingStrategy;
    }
}
