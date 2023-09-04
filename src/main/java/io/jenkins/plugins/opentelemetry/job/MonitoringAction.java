/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.model.Action;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.lang.ref.SoftReference;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MonitoringAction implements Action, RunAction2, SimpleBuildStep.LastBuildAction {
    private final static Logger LOGGER = Logger.getLogger(MonitoringAction.class.getName());

    private transient Span span;
    final private String traceId;
    final private String spanId;
    final private String rootSpanName;
    private Map<String, Map<String, String>> contextPerNodeId = new HashMap<>();
    final private Map<String, String> rootContext;

    private transient Run run;

    public MonitoringAction(Span span) {
        this.traceId = span.getSpanContext().getTraceId();
        this.spanId = span.getSpanContext().getSpanId();
        this.rootSpanName = span instanceof ReadWriteSpan ? ((ReadWriteSpan) span).getName() : null; // when tracer is no-op, span is NOT a ReadWriteSpan
        this.span = span;
        try (Scope scope = span.makeCurrent()) {
            Map<String, String> context = new HashMap<>();
            W3CTraceContextPropagator.getInstance().inject(Context.current(), context, (carrier, key, value) -> carrier.put(key, value));
            this.rootContext = context;
        }
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "OpenTelemetry";
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return run.getParent().getLastSuccessfulBuild().getActions(MonitoringAction.class);
    }

    @Override
    public String getUrlName() {
        return null;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getSpanName() {
        return rootSpanName;
    }

    @CheckForNull
    public Span getSpan() {
        return span;
    }

    public void purgeSpan(){
        LOGGER.log(Level.INFO, () -> "Purge span='" + rootSpanName + "', spanId=" + spanId + ", traceId=" + traceId + ": " + (span == null ? "#null#" : "purged"));
        this.span = null;
    }

    /**
     * Add per {@link FlowNode} contextual information.
     */
    public void addContext(@NonNull FlowNode node, @NonNull Map<String, String> context) {
        if (contextPerNodeId == null) {
            contextPerNodeId = new HashMap<>();
        }
        contextPerNodeId.put(node.getId(), context);
    }

    @NonNull
    public Map<String, String> getRootContext() {
        return rootContext;
    }

    @CheckForNull
    public Map<String, String> getContext(@NonNull String flowNodeId) {
        return contextPerNodeId.get(flowNodeId);
    }

    @NonNull
    public List<ObservabilityBackendLink> getLinks() {
        List<ObservabilityBackend> tracingCapableBackends = JenkinsOpenTelemetryPluginConfiguration.get().getObservabilityBackends()
            .stream()
            .filter(backend -> backend.getTraceVisualisationUrlTemplate() != null)
            .collect(Collectors.toList());

        if (tracingCapableBackends.isEmpty()) {
            return Collections.singletonList(new ObservabilityBackendLink(
                "Please define an OpenTelemetry Visualisation URL of pipelines in Jenkins configuration",
                Jenkins.get().getRootUrl() + "/configure",
                "icon-gear2",
                null));
        }
        Map<String, Object> binding = new HashMap<>();
        binding.put("serviceName", Objects.requireNonNull(JenkinsOpenTelemetryPluginConfiguration.get().getServiceName()));
        binding.put("rootSpanName", this.rootSpanName == null ? null : OtelUtils.urlEncode(this.rootSpanName));
        binding.put("traceId", this.traceId);
        binding.put("spanId", this.spanId);
        binding.put("startTime", Instant.ofEpochMilli(run.getStartTimeInMillis()));

        return tracingCapableBackends.stream().map(backend ->
            new ObservabilityBackendLink(
                "View pipeline with " + backend.getName(),
                backend.getTraceVisualisationUrl(binding),
                backend.getIconPath(),
                backend.getEnvVariableName())
        ).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "MonitoringAction{" +
            "traceId='" + traceId + '\'' +
            ", spanId='" + spanId + '\'' +
            ", span.name='" + rootSpanName + '\'' +
            ", run='" + run + '\'' +
            '}';
    }

    public static class ObservabilityBackendLink {
        final String label;
        final String url;
        final String iconClass;
        final String environmentVariableName;

        public ObservabilityBackendLink(String label, String url, String iconClass, String environmentVariableName) {
            this.label = label;
            this.url = url;
            this.iconClass = iconClass;
            this.environmentVariableName = environmentVariableName;
        }

        public String getLabel() {
            return label;
        }

        public String getUrl() {
            return url;
        }

        public String getIconClass() {
            return iconClass;
        }

        public String getEnvironmentVariableName() {
            return environmentVariableName;
        }

        @Override
        public String toString() {
            return "ObservabilityBackendLink{" +
                "label='" + label + '\'' +
                ", url='" + url + '\'' +
                ", iconUrl='" + iconClass + '\'' +
                ", environmentVariableName='" + environmentVariableName + '\'' +
                '}';
        }
    }
}
