/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
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
        if (plannedNodes.size() != 1) {
            return;
        }
        NodeProvisioner.PlannedNode plannedNode = plannedNodes.iterator().next();

        String rootSpanName = cloud.getUrl();
        JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault("cloud", cloud.getDescriptor());
        SpanBuilder rootSpanBuilder = getTracer().spanBuilder(rootSpanName).setSpanKind(SpanKind.SERVER);

        // TODO move this to a pluggable span enrichment API with implementations for different observability backends
        // Regarding the value `unknown`, see https://github.com/jenkinsci/opentelemetry-plugin/issues/51
        rootSpanBuilder
            .setAttribute(JenkinsOtelSemanticAttributes.ELASTIC_TRANSACTION_TYPE, "unknown")
            .setAttribute(JenkinsOtelSemanticAttributes.CI_CLOUD_LABEL, label.getExpression())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());

        // ENRICH attributes with every Cloud specifics

        // START root span
        Span rootSpan = rootSpanBuilder.startSpan();

        this.getTraceService().putSpan(plannedNode, rootSpan);
        try (final Scope rootSpanScope = rootSpan.makeCurrent()) {
            LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - begin root " + OtelUtils.toDebugString(rootSpan));

            // START started span
            Span startSpan = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.CLOUD_SPAN_PHASE_STARTED_NAME)
                .setParent(Context.current().with(rootSpan))
                .startSpan();
            LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - begin started " + OtelUtils.toDebugString(startSpan));

            this.getTraceService().putSpan(plannedNode, startSpan);
            startSpan.makeCurrent();
        }
    }

    @Override
    public void _onCommit(@NonNull NodeProvisioner.PlannedNode plannedNode, @NonNull Node node) {
        LOGGER.log(Level.FINE, () -> "_onCommit(" + node + ")");
        try (Scope parentScope = endCloudPhaseSpan(plannedNode)) {
            Span runSpan = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.CLOUD_SPAN_PHASE_COMMIT_NAME).setParent(Context.current()).startSpan();
            LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - begin " + OtelUtils.toDebugString(runSpan));
            runSpan.makeCurrent();
            this.getTraceService().putSpan(plannedNode, runSpan);
        }
    }

    @Override
    public void _onFailure(NodeProvisioner.PlannedNode plannedNode, Throwable t) {
        LOGGER.log(Level.FINE, () -> "_onFailure(" + plannedNode + ")");
        failureCloudCounter.add(1);
        try (Scope parentScope = endCloudPhaseSpan(plannedNode)) {
            Span span = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.CLOUD_SPAN_PHASE_FAILURE_NAME).setParent(Context.current()).startSpan();
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getMessage());
            span.end();
            LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - begin " + OtelUtils.toDebugString(span));
        }
    }

    @Override
    public void _onRollback(@NonNull NodeProvisioner.PlannedNode plannedNode, @NonNull Node node,
                            @NonNull Throwable t){
        LOGGER.log(Level.FINE, () -> "_onRollback(" + plannedNode + ")");
        failureCloudCounter.add(1);
    }

    @Override
    public void _onComplete(NodeProvisioner.PlannedNode plannedNode, Node node) {
        LOGGER.log(Level.FINE, () -> "_onComplete(" + plannedNode + ")");
        totalCloudCount.add(1);
        try (Scope parentScope = endCloudPhaseSpan(plannedNode)) {
            Span span = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.CLOUD_SPAN_PHASE_COMPLETE_NAME).setParent(Context.current()).startSpan();
            span.setStatus(StatusCode.OK);
            span.end();
            LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - begin " + OtelUtils.toDebugString(span));
        }
    }

    @MustBeClosed
    @Nonnull
    protected Scope endCloudPhaseSpan(@NonNull NodeProvisioner.PlannedNode plannedNode) {
        Span cloudPhaseSpan = verifyNotNull(Span.current(), "No cloudPhaseSpan found in context");
        cloudPhaseSpan.end();
        LOGGER.log(Level.FINE, () -> plannedNode.displayName + " - end " + OtelUtils.toDebugString(cloudPhaseSpan));

        //this.getTraceService().removeJobPhaseSpan(run, pipelinePhaseSpan);
        Span newCurrentSpan = this.getTraceService().getSpan(plannedNode);
        Scope newScope = newCurrentSpan.makeCurrent();
        Context.current().with(PlannedNodeContextKey.KEY, plannedNode);
        return newScope;
    }
}
