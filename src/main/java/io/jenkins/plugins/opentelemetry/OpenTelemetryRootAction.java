/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import hudson.Extension;
import hudson.model.RootAction;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Decorates Jenkins navigation GUI with the OpenTelemetry dashboard link if defined
 */
@Extension
public class OpenTelemetryRootAction implements RootAction {
    private static final Logger logger = Logger.getLogger(OpenTelemetryRootAction.class.getName());

    private JenkinsOpenTelemetryPluginConfiguration pluginConfiguration;
    private ReconfigurableOpenTelemetry openTelemetry;

    public Optional<ObservabilityBackend> getFirstMetricsCapableObservabilityBackend() {
        final Optional<ObservabilityBackend> observabilityBackend =
                pluginConfiguration.getObservabilityBackends().stream()
                        .filter(backend -> backend.getMetricsVisualizationUrlTemplate() != null)
                        .findFirst();
        logger.log(
                Level.FINE, () -> "getFirstMetricsCapableObservabilityBackend: " + observabilityBackend.orElse(null));
        return observabilityBackend;
    }

    @Override
    public String getIconFileName() {
        return getFirstMetricsCapableObservabilityBackend()
                .map(ObservabilityBackend::getIconPath)
                .map(icon -> icon + " icon-md")
                .orElse(null);
    }

    @Override
    public String getDisplayName() {
        return getFirstMetricsCapableObservabilityBackend()
                .map(ObservabilityBackend::getName)
                .orElse(null);
    }

    @Override
    public String getUrlName() {
        // TODO we could keep in cache this URL
        return getFirstMetricsCapableObservabilityBackend()
                .map(backend -> backend.getMetricsVisualizationUrl(this.openTelemetry.getResource()))
                .orElse(null);
    }

    @Inject
    public void setJenkinsOpenTelemetryPluginConfiguration(
            JenkinsOpenTelemetryPluginConfiguration pluginConfiguration) {
        this.pluginConfiguration = pluginConfiguration;
    }

    @Inject
    public void setOpenTelemetry(ReconfigurableOpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }
}
