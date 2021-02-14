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
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;

import javax.annotation.CheckForNull;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

public class MonitoringAction implements Action, RunAction2 {
    private final static Logger LOGGER = Logger.getLogger(MonitoringAction.class.getName());

    final String traceId;
    final String spanId;
    final String rootSpanName;
    transient Run run;
    transient JenkinsOpenTelemetryPluginConfiguration pluginConfiguration;

    public MonitoringAction(String rootSpanName, String traceId, String spanId) {
        this.rootSpanName = rootSpanName;
        this.traceId = traceId;
        this.spanId = spanId;
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
    public String getUrlName() {
        return null;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    @CheckForNull
    public List<ObservabilityBackendLink> getLinks() {
        List<ObservabilityBackend> observabilityBackends = this.pluginConfiguration.getObservabilityBackends();

        if (observabilityBackends.isEmpty()) {
            return Collections.singletonList(new ObservabilityBackendLink(
                    "Please define an OpenTelemetry Visualisation URL of pipelines in Jenkins configuration",
                    Jenkins.get().getRootUrl() + "/configure",
                    "/images/48x48/gear2.png",
                    null));
        }
        Map<String, Object> binding = new HashMap<>();
        binding.put("serviceName", JenkinsOtelSemanticAttributes.SERVICE_NAME_JENKINS);
        binding.put("rootSpanName", this.rootSpanName);
        binding.put("traceId", this.traceId);
        binding.put("spanId", this.spanId);
        binding.put("startTime", Instant.ofEpochMilli(run.getStartTimeInMillis()));

        List<ObservabilityBackendLink> links = new ArrayList<>();
        for (ObservabilityBackend observabilityBackend : observabilityBackends) {
            links.add(new ObservabilityBackendLink(
                    "View pipeline with " + observabilityBackend.getDescriptor().getDisplayName(),
                    observabilityBackend.getTraceVisualisationUrl(binding),
                    observabilityBackend.getIconPath(),
                    observabilityBackend.getEnvVariable()));

        }
        return links;
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
		final String envVar;

        public ObservabilityBackendLink(String label, String url, String iconUrl, String envVar) {
            this.label = label;
            this.url = url;
            this.iconUrl = iconUrl;
            this.envVar = envVar;
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

        public String getEnvVar() {
            return envVar;
        }

        @Override
        public String toString() {
            return "ObservabilityBackendLink{" +
                    "label='" + label + '\'' +
                    ", url='" + url + '\'' +
                    ", iconUrl='" + iconUrl + '\'' +
                    ", envVar='" + envVar + '\'' +
                    '}';
        }
    }
}
