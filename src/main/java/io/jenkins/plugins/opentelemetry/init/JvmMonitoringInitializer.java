/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.opentelemetry.api.OpenTelemetry;
/*
 * NOTE: in instrumentation 2.x, the following classes will suffer from the following changes:
 * - io.opentelemetry.instrumentation.runtimemetrics.java8.* -> io.opentelemetry.instrumentation.runtimemetrics.java8¡.internal.Experimental*
 * - The metrics process.runtime.jvm.* are moved to jvm.* ans some are renamed see https://github.com/open-telemetry/semantic-conventions/issues/42 and related issues
 */
import io.opentelemetry.instrumentation.runtimemetrics.java8.BufferPools;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Classes;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Cpu;
import io.opentelemetry.instrumentation.runtimemetrics.java8.GarbageCollector;
import io.opentelemetry.instrumentation.runtimemetrics.java8.MemoryPools;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Threads;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jenkins.YesNoMaybe;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Inspired by io.opentelemetry.instrumentation.javaagent.runtimemetrics.RuntimeMetricsInstaller
 */
@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class JvmMonitoringInitializer implements OtelComponent {

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
