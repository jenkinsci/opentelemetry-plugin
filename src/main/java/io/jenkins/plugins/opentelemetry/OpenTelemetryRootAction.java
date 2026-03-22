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

    /**
     * Returns the first configured backend that supports metrics visualization links.
     *
     * @return optional metrics-capable backend
     */
    public Optional<ObservabilityBackend> getFirstMetricsCapableObservabilityBackend() {
        final Optional<ObservabilityBackend> observabilityBackend =
                pluginConfiguration.getObservabilityBackends().stream()
                        .filter(backend -> backend.getMetricsVisualizationUrlTemplate() != null)
                        .findFirst();
        logger.log(
                Level.FINE, () -> "getFirstMetricsCapableObservabilityBackend: " + observabilityBackend.orElse(null));
        return observabilityBackend;
    }

    /**
     * Returns the icon file name used in Jenkins navigation.
     *
     * @return icon path and size class, or {@code null} when no metrics backend is configured
     */
    @Override
    public String getIconFileName() {
        return getFirstMetricsCapableObservabilityBackend()
                .map(ObservabilityBackend::getIconPath)
                .map(icon -> icon + " icon-md")
                .orElse(null);
    }

    /**
     * Returns the navigation display name derived from the metrics backend name.
     *
     * @return backend display name, or {@code null} when no metrics backend is configured
     */
    @Override
    public String getDisplayName() {
        return getFirstMetricsCapableObservabilityBackend()
                .map(ObservabilityBackend::getName)
                .orElse(null);
    }

    /**
     * Returns the metrics visualization URL for Jenkins navigation.
     *
     * @return backend metrics URL, or {@code null} when no metrics backend is configured
     */
    @Override
    public String getUrlName() {
        // TODO we could keep in cache this URL
        return getFirstMetricsCapableObservabilityBackend()
                .map(backend -> backend.getMetricsVisualizationUrl(this.openTelemetry.getResource()))
                .orElse(null);
    }

    /**
     * Injects global plugin configuration used by this root action.
     *
     * @param pluginConfiguration global plugin configuration
     */
    @Inject
    public void setJenkinsOpenTelemetryPluginConfiguration(
            JenkinsOpenTelemetryPluginConfiguration pluginConfiguration) {
        this.pluginConfiguration = pluginConfiguration;
    }

    /**
     * Injects the OpenTelemetry facade used to resolve resource attributes.
     *
     * @param openTelemetry reconfigurable OpenTelemetry facade
     */
    @Inject
    public void setOpenTelemetry(ReconfigurableOpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }
}
