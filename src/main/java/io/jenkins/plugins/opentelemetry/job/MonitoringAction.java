/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.ExtensionList;
import hudson.model.Action;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MonitoringAction implements Action, RunAction2, SimpleBuildStep.LastBuildAction {
    private final static Logger LOGGER = Logger.getLogger(MonitoringAction.class.getName());

    final String traceId;
    final String spanId;
    final String rootSpanName;
    Map<String, Map<String, String>> contextPerNodeId = new HashMap<>();
    Map<String, String> rootContext = new HashMap<>();

    transient Run run;
    transient JenkinsOpenTelemetryPluginConfiguration pluginConfiguration;

    public MonitoringAction(String traceId, String spanId, String rootSpanName) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.rootSpanName = rootSpanName;
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        this.run = r;
        this.pluginConfiguration = ExtensionList.lookupSingleton(JenkinsOpenTelemetryPluginConfiguration.class);
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
        this.pluginConfiguration = ExtensionList.lookupSingleton(JenkinsOpenTelemetryPluginConfiguration.class);
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

    /**
     * Add per {@link FlowNode} contextual information.
     */
    public void addContext(@Nonnull FlowNode node, @Nonnull Map<String, String> context) {
        if (contextPerNodeId == null) {
            contextPerNodeId = new HashMap<>();
        }
        contextPerNodeId.put(node.getId(), context);
    }

    @CheckForNull
    public Map<String, String> getRootContext() {
        return rootContext;
    }

    /**
     * See `io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator#inject(io.opentelemetry.context.Context, java.lang.Object, io.opentelemetry.context.propagation.TextMapSetter)`
     */
    public void addRootContext(@Nonnull Map<String, String> context) {
        this.rootContext = context;
    }

    @CheckForNull
    public Map<String, String> getContext(@Nonnull String flowNodeId) {
        return contextPerNodeId.get(flowNodeId);
    }

    @Nonnull
    public List<ObservabilityBackendLink> getLinks() {
        List<ObservabilityBackend> tracingCapableBackends = this.pluginConfiguration.getObservabilityBackends()
            .stream()
            .filter(backend -> backend.getTraceVisualisationUrlTemplate() != null)
            .collect(Collectors.toList());

        if (tracingCapableBackends.isEmpty()) {
            return Collections.singletonList(new ObservabilityBackendLink(
                "Please define an OpenTelemetry Visualisation URL of pipelines in Jenkins configuration",
                Jenkins.get().getRootUrl() + "/configure",
                "/images/48x48/gear2.png",
                null));
        }
        Map<String, Object> binding = new HashMap<>();
        binding.put("serviceName", Objects.requireNonNull(JenkinsOpenTelemetryPluginConfiguration.get().getServiceName()));
        binding.put("rootSpanName", this.rootSpanName);
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
            ", run='" + run + '\'' +
            '}';
    }

    public static class ObservabilityBackendLink {
        final String label;
        final String url;
        final String iconUrl;
        final String environmentVariableName;

        public ObservabilityBackendLink(String label, String url, String iconUrl, String environmentVariableName) {
            this.label = label;
            this.url = url;
            this.iconUrl = iconUrl;
            this.environmentVariableName = environmentVariableName;
        }

        public String getLabel() {
            return label;
        }

        public String getUrl() {
            return url;
        }

        public String getIconUrl() {
            return iconUrl;
        }

        public String getEnvironmentVariableName() {
            return environmentVariableName;
        }

        @Override
        public String toString() {
            return "ObservabilityBackendLink{" +
                "label='" + label + '\'' +
                ", url='" + url + '\'' +
                ", iconUrl='" + iconUrl + '\'' +
                ", environmentVariableName='" + environmentVariableName + '\'' +
                '}';
        }
    }
}
