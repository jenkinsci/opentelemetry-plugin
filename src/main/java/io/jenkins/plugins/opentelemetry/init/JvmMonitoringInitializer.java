/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.opentelemetry.instrumentation.runtimemetrics.GarbageCollector;
import io.jenkins.plugins.opentelemetry.opentelemetry.instrumentation.runtimemetrics.MemoryPools;
import jenkins.YesNoMaybe;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class JvmMonitoringInitializer extends OpenTelemetryPluginAbstractInitializer {

    private final static Logger LOGGER = Logger.getLogger(JvmMonitoringInitializer.class.getName());

    public JvmMonitoringInitializer() {

    }

    /**
     * TODO better dependency handling
     * Don't start just after `PLUGINS_STARTED` because it creates an initialization problem
     */
    @Initializer(after = InitMilestone.JOB_LOADED)
    public void initialize() {
        LOGGER.log(Level.INFO, "Start monitoring the JVM...");
        GarbageCollector.registerObservers();
        MemoryPools.registerObservers();
    }
}
