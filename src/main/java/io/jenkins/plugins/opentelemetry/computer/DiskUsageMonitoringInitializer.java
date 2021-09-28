/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import com.cloudbees.simplediskusage.DiskItem;
import com.cloudbees.simplediskusage.QuickDiskUsagePlugin;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.slaves.ComputerListener;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.metrics.Meter;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Note: we extend {@link ComputerListener} instead of a plain {@link ExtensionPoint} because simple ExtensionPoint don't get automatically loaded by Jenkins
 * There may be a better API to do this.
 */
@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class DiskUsageMonitoringInitializer extends ComputerListener {

    private final static Logger LOGGER = Logger.getLogger(DiskUsageMonitoringInitializer.class.getName());

    protected Meter meter;

    public DiskUsageMonitoringInitializer() {
    }

    @PostConstruct
    public void postConstruct() {
        final Jenkins jenkins = Jenkins.get();
        QuickDiskUsagePlugin diskUsagePlugin = jenkins.getPlugin(QuickDiskUsagePlugin.class);
        if (diskUsagePlugin == null) {
            LOGGER.log(Level.WARNING, () -> "Plugin 'disk-usage' not loaded, don't start monitoring Jenkins controller disk usage");
        } else {
            meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_DISK_USAGE_BYTES)
                .ofLongs()
                .setDescription("Disk usage of first level folder in JENKINS_HOME.")
                .setUnit("byte")
                .buildWithCallback(valueObserver -> valueObserver.observe(calculateDiskUsageInBytes(diskUsagePlugin)));
            LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins controller disk usage");
        }
    }

    private long calculateDiskUsageInBytes(@Nonnull QuickDiskUsagePlugin diskUsagePlugin) {
        try {
            DiskItem disk = diskUsagePlugin.getDirectoriesUsages()
                    .stream()
                    .filter(x -> x.getDisplayName().equals("JENKINS_HOME"))
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
    public void setMeter(@Nonnull OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.meter = openTelemetrySdkProvider.getMeter();
    }
}
