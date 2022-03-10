/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import com.cloudbees.simplediskusage.DiskItem;
import com.cloudbees.simplediskusage.QuickDiskUsageInitializer;
import com.cloudbees.simplediskusage.QuickDiskUsagePlugin;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.metrics.Meter;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Capture disk usage metrics relying on the {@link QuickDiskUsagePlugin}
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class DiskUsageMonitoringInitializer {

    private final static Logger LOGGER = Logger.getLogger(DiskUsageMonitoringInitializer.class.getName());

    protected Meter meter;

    /**
     * Don't inject the `quickDiskUsagePlugin` using @{@link  Inject} because the injected instance is not the right once.
     * Lazy load it using {@link Jenkins#getPlugin(Class)}.
     */
    protected QuickDiskUsagePlugin quickDiskUsagePlugin;

    /**
     * TODO ensure initialized after {@link QuickDiskUsageInitializer#initialize()} has been invoked by Jenkins
     * lifecycle before {@link io.opentelemetry.api.metrics.ObservableLongMeasurement#record(long)} is invoked
     */
    @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
    public void initialize() {
            meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_DISK_USAGE_BYTES)
                .ofLongs()
                .setDescription("Disk usage of first level folder in JENKINS_HOME.")
                .setUnit("byte")
                .buildWithCallback(valueObserver -> valueObserver.record(calculateDiskUsageInBytes()));
            LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins controller disk usage");
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
    private long calculateDiskUsageInBytes(@NonNull QuickDiskUsagePlugin diskUsagePlugin) {
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

    /**
     * Jenkins doesn't support {@link com.google.inject.Provides} so we manually wire dependencies :-(
     */
    @Inject
    public void setMeter(@NonNull OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.meter = openTelemetrySdkProvider.getMeter();
    }
}
