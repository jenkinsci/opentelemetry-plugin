/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.OpenTelemetryLifecycleListener;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.java8.*;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jenkins.YesNoMaybe;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Inspired by io.opentelemetry.instrumentation.javaagent.runtimemetrics.RuntimeMetricsInstaller
 */
@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class JvmMonitoringInitializer implements OpenTelemetryLifecycleListener {

    private final static Logger LOGGER = Logger.getLogger(JvmMonitoringInitializer.class.getName());

    public JvmMonitoringInitializer() {

    }

    @Override
    public void afterSdkInitialized(OpenTelemetry openTelemetry, ConfigProperties config) {

        boolean defaultEnabled = config.getBoolean("otel.instrumentation.common.default-enabled", true);
        if (!config.getBoolean("otel.instrumentation.runtime-metrics.enabled", defaultEnabled)) {
            return;
        }

        BufferPools.registerObservers(openTelemetry);
        Classes.registerObservers(openTelemetry);
        Cpu.registerObservers(openTelemetry);
        GarbageCollector.registerObservers(openTelemetry);
        MemoryPools.registerObservers(openTelemetry);
        Threads.registerObservers(openTelemetry);
        LOGGER.log(Level.FINE, "Start monitoring Jenkins JVM...");
    }
}
