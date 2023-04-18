/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import com.cloudbees.simplediskusage.DiskItem;
import com.cloudbees.simplediskusage.QuickDiskUsagePlugin;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Capture disk usage metrics relying on the {@link QuickDiskUsagePlugin}
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class DiskUsageMonitoringInitializer implements OtelComponent {

    private final static Logger LOGGER = Logger.getLogger(DiskUsageMonitoringInitializer.class.getName());

    /**
     * Don't inject the `quickDiskUsagePlugin` using @{@link  Inject} because the injected instance is not the right once.
     * Lazy load it using {@link Jenkins#getPlugin(Class)}.
     */
    protected QuickDiskUsagePlugin quickDiskUsagePlugin;

    @Override
    public void afterSdkInitialized(Meter meter, LoggerProvider loggerProvider, EventEmitter eventEmitter, Tracer tracer, ConfigProperties configProperties) {
            meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_DISK_USAGE_BYTES)
                .ofLongs()
                .setDescription("Disk usage of first level folder in JENKINS_HOME.")
                .setUnit("byte")
                .buildWithCallback(valueObserver -> valueObserver.record(calculateDiskUsageInBytes()));

        LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins controller disk usage...");
    }

    private long calculateDiskUsageInBytes() {
        if (this.quickDiskUsagePlugin == null) {
            Jenkins jenkins = Jenkins.get();
            QuickDiskUsagePlugin quickDiskUsagePlugin = jenkins.getPlugin(QuickDiskUsagePlugin.class);
            if (quickDiskUsagePlugin == null) return 0l;
            this.quickDiskUsagePlugin = quickDiskUsagePlugin;
        }
        return calculateDiskUsageInBytes(quickDiskUsagePlugin);
    }

    private long calculateDiskUsageInBytes(@Nonnull QuickDiskUsagePlugin diskUsagePlugin) {
        LOGGER.log(Level.FINE, "calculateDiskUsageInBytes");
        try {
            DiskItem disk = diskUsagePlugin.getDirectoriesUsages()
                .stream()
                .filter(diskItem -> diskItem.getDisplayName().equals("JENKINS_HOME"))
                .findFirst()
                .orElse(null);
            if (disk == null) {
                return 0;
            } else {
                return disk.getUsage() * 1024;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e, () -> "Exception invoking `diskUsagePlugin.getDirectoriesUsages()`");
            return 0;
        }
    }
}
