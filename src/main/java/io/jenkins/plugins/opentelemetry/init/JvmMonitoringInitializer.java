/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import jenkins.YesNoMaybe;

/**
 * Inspired by io.opentelemetry.instrumentation.javaagent.runtimemetrics.RuntimeMetricsInstaller
 * TODO support reconfiguration of <code>otel.instrumentation.runtime-metrics.enabled=false</code>
 */
@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class JvmMonitoringInitializer implements OpenTelemetryLifecycleListener {

    private static final Logger LOGGER = Logger.getLogger(JvmMonitoringInitializer.class.getName());

    @Inject
    protected ReconfigurableOpenTelemetry openTelemetry;

    private RuntimeMetrics runtimeMetrics;

    @PostConstruct
    public void postConstruct() {
        ConfigProperties config = openTelemetry.getConfig();
        boolean defaultEnabled = config.getBoolean("otel.instrumentation.common.default-enabled", true);
        if (!config.getBoolean("otel.instrumentation.runtime-metrics.enabled", defaultEnabled)) {
            LOGGER.log(
                    Level.FINE,
                    "Jenkins Controller JVM is disabled by config and reconfiguration requires restart ...");
            return;
        }

        LOGGER.log(Level.FINE, "Start monitoring Jenkins Controller JVM...");
        // Use RuntimeMetrics (Java 17+) with experimental features enabled
        // Combines JMX metrics (Java 8+) and JFR metrics (Java 17+)
        runtimeMetrics = RuntimeMetrics.builder(openTelemetry)
                .enableFeature(JfrFeature.BUFFER_METRICS)
                .enableFeature(JfrFeature.CLASS_LOAD_METRICS)
                .enableFeature(JfrFeature.CPU_UTILIZATION_METRICS)
                .enableFeature(JfrFeature.GC_DURATION_METRICS)
                .enableFeature(JfrFeature.MEMORY_POOL_METRICS)
                .enableFeature(JfrFeature.THREAD_METRICS)
                .build();
    }
}
