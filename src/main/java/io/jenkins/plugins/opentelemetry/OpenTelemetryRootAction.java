/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import hudson.Extension;
import hudson.model.RootAction;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;

import javax.inject.Inject;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class OpenTelemetryRootAction implements RootAction {
    private static final Logger logger = Logger.getLogger(OpenTelemetryRootAction.class.getName());

    private JenkinsOpenTelemetryPluginConfiguration pluginConfiguration;

    public Optional<ObservabilityBackend> getFirstMetricsCapableObservabilityBackend() {
        final Optional<ObservabilityBackend> observabilityBackend = Optional.ofNullable(pluginConfiguration.getObservabilityBackends().stream().filter(backend -> backend.getMetricsVisualisationUrl() != null).findFirst().orElse(null));
        logger.log(Level.FINE,() -> "OpenTelemetryRootAction.observabilityBackend: " + observabilityBackend);
        return observabilityBackend;
    }

    @Override
    public String getIconFileName() {
        return getFirstMetricsCapableObservabilityBackend().map(backend -> backend.getIconPath()).orElse(null);
    }

    @Override
    public String getDisplayName() {
        return getFirstMetricsCapableObservabilityBackend().map(backend -> backend.getName()).orElse(null);
    }

    @Override
    public String getUrlName() {
        return getFirstMetricsCapableObservabilityBackend().map(backend -> backend.getMetricsVisualisationUrl()).orElse(null);
    }

    @Inject
    public void setJenkinsOpenTelemetryPluginConfiguration(JenkinsOpenTelemetryPluginConfiguration pluginConfiguration) {
        this.pluginConfiguration = pluginConfiguration;
    }
}
