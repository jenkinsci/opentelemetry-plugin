/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import groovy.text.GStringTemplateEngine;
import groovy.text.Template;
import hudson.ExtensionList;
import hudson.model.Action;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;

import javax.annotation.CheckForNull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
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
    public String getLinkUrl() {
        Template template = this.pluginConfiguration.getTraceVisualisationUrlGTemplate();

        if (template == null) {
            return Jenkins.get().getRootUrl() + "/configure";
        } else {
            Map<String, Object> binding = new HashMap<>();
            binding.put("baseUrl", this.pluginConfiguration.getTraceVisualisationBaseUrl());
            binding.put("serviceName", JenkinsOtelSemanticAttributes.SERVICE_NAME_JENKINS);
            binding.put("rootSpanName", this.rootSpanName);
            binding.put("traceId", this.traceId);
            binding.put("spanId", this.spanId);
            binding.put("startTime", Instant.ofEpochMilli(run.getStartTimeInMillis()));
            return template.make(binding).toString();
        }
    }

    public String getLinkCaption() {
        Template template = this.pluginConfiguration.getTraceVisualisationUrlGTemplate();

        if (template == null) {
            return "Please define an OpenTelemetry Visualisation URL of pipelines in Jenkins configuration";
        } else {
            return "View pipeline execution (traceId: " + traceId + ")";
        }
    }

    @Override
    public String toString() {
        return "MonitoringAction{" +
                "traceId='" + traceId + '\'' +
                ", spanId='" + spanId + '\'' +
                ", run='" + run + '\'' +
                '}';
    }
}
