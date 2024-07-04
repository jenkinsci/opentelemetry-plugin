/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Classes;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Cpu;
import io.opentelemetry.instrumentation.runtimemetrics.java8.GarbageCollector;
import io.opentelemetry.instrumentation.runtimemetrics.java8.MemoryPools;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Threads;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.ExperimentalBufferPools;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jenkins.YesNoMaybe;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Inspired by io.opentelemetry.instrumentation.javaagent.runtimemetrics.RuntimeMetricsInstaller
 * TODO support reconfiguration of <code>otel.instrumentation.runtime-metrics.enabled=false</code>
 */
@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class JvmMonitoringInitializer implements OpenTelemetryLifecycleListener {

    private final static Logger LOGGER = Logger.getLogger(JvmMonitoringInitializer.class.getName());

    @Inject
    protected ReconfigurableOpenTelemetry openTelemetry;

    @PostConstruct
    public void postConstruct() {
        ConfigProperties config = openTelemetry.getConfig();
        boolean defaultEnabled = config.getBoolean("otel.instrumentation.common.default-enabled", true);
        if (!config.getBoolean("otel.instrumentation.runtime-metrics.enabled", defaultEnabled)) {
            LOGGER.log(Level.FINE, "Jenkins Controller JVM is disabled by config and reconfiguration requires restart ...");
            return;
        }

        LOGGER.log(Level.FINE, "Start monitoring Jenkins Controller JVM...");
        // should we enable the experimental buffer pools instrumentation?
        ExperimentalBufferPools.registerObservers(openTelemetry);
        Classes.registerObservers(openTelemetry);
        Cpu.registerObservers(openTelemetry);
        GarbageCollector.registerObservers(openTelemetry);
        MemoryPools.registerObservers(openTelemetry);
        Threads.registerObservers(openTelemetry);
    }
}
