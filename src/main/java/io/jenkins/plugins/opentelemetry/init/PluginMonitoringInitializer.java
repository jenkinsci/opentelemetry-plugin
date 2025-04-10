/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import hudson.PluginManager;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes.STATUS;
import static io.jenkins.plugins.opentelemetry.semconv.JenkinsMetrics.JENKINS_PLUGINS;
import static io.jenkins.plugins.opentelemetry.semconv.JenkinsMetrics.JENKINS_PLUGINS_UPDATES;

/**
 * <p>
 * Monitor the Jenkins plugins
 * </p>
 * <p>
 * TODO report on `hasUpdate` plugin count.
 * </p>
 */
@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class PluginMonitoringInitializer implements OpenTelemetryLifecycleListener {

    private static final Logger logger = Logger.getLogger(PluginMonitoringInitializer.class.getName());

    @Inject
    JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    @PostConstruct
    public void postConstruct() {

        logger.log(Level.FINE, () -> "Start monitoring Jenkins plugins...");

        Meter meter = Objects.requireNonNull(jenkinsControllerOpenTelemetry).getDefaultMeter();

        final ObservableLongMeasurement plugins = meter
            .gaugeBuilder(JENKINS_PLUGINS)
            .setUnit("${plugins}")
            .setDescription("Jenkins plugins")
            .ofLongs()
            .buildObserver();
        final ObservableLongMeasurement pluginUpdates = meter
            .gaugeBuilder(JENKINS_PLUGINS_UPDATES)
            .setUnit("${plugins}")
            .setDescription("Jenkins plugin updates")
            .ofLongs()
            .buildObserver();
        meter.batchCallback(() -> {
            logger.log(Level.FINE, () -> "Recording Jenkins controller executor pool metrics...");

            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                logger.log(Level.FINE, () -> "Jenkins instance is null, skipping plugin monitoring metrics recording");
                return;
            }

            AtomicInteger active = new AtomicInteger();
            AtomicInteger inactive = new AtomicInteger();
            AtomicInteger hasUpdate = new AtomicInteger();
            AtomicInteger isUpToDate = new AtomicInteger();

            PluginManager pluginManager = jenkins.getPluginManager();
            pluginManager.getPlugins().forEach(plugin -> {
                if (plugin.isActive()) {
                    active.incrementAndGet();
                } else {
                    inactive.incrementAndGet();
                }
                if (plugin.hasUpdate()) {
                    hasUpdate.incrementAndGet();
                } else {
                    isUpToDate.incrementAndGet();
                }
            });
            int failed = pluginManager.getFailedPlugins().size();
            plugins.record(active.get(), Attributes.of(STATUS, "active"));
            plugins.record(inactive.get(), Attributes.of(STATUS, "inactive"));
            plugins.record(failed, Attributes.of(STATUS, "failed"));
            pluginUpdates.record(hasUpdate.get(), Attributes.of(STATUS, "hasUpdate"));
            pluginUpdates.record(isUpToDate.get(), Attributes.of(STATUS, "isUpToDate"));
        }, plugins, pluginUpdates);
    }
}
