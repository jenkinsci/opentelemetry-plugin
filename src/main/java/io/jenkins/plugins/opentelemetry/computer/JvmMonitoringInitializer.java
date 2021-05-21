/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.slaves.ComputerListener;
import io.jenkins.plugins.opentelemetry.opentelemetry.instrumentation.runtimemetrics.GarbageCollector;
import io.jenkins.plugins.opentelemetry.opentelemetry.instrumentation.runtimemetrics.MemoryPools;
import jenkins.YesNoMaybe;

import javax.annotation.PostConstruct;
import java.util.logging.Logger;

/**
 * Note: we extend {@link ComputerListener} instead of a plain {@link ExtensionPoint} because simple ExtensionPoint don't get automatically loaded by Jenkins
 * There may be a better API to do this.
 */
@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class JvmMonitoringInitializer extends ComputerListener {

    private final static Logger LOGGER = Logger.getLogger(JvmMonitoringInitializer.class.getName());

    public JvmMonitoringInitializer() {
    }

    @PostConstruct
    public void postConstruct() {
        GarbageCollector.registerObservers();
        MemoryPools.registerObservers();
    }
}
