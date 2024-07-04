/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import com.cloudbees.simplediskusage.DiskItem;
import com.cloudbees.simplediskusage.QuickDiskUsagePlugin;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.metrics.Meter;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Capture disk usage metrics relying on the {@link QuickDiskUsagePlugin}
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class DiskUsageMonitoringInitializer implements OpenTelemetryLifecycleListener {

    private final static Logger LOGGER = Logger.getLogger(DiskUsageMonitoringInitializer.class.getName());

    /**
     * Don't inject the `quickDiskUsagePlugin` using @{@link  Inject} because the injected instance is not the right one.
     * Lazy load it using {@link Jenkins#getPlugin(Class)}.
     */
    protected QuickDiskUsagePlugin quickDiskUsagePlugin;

    @Inject
    protected JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    @PostConstruct
    public void postConstruct() {
        LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins controller disk usage...");

        Meter meter = Objects.requireNonNull(jenkinsControllerOpenTelemetry).getDefaultMeter();
        meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_DISK_USAGE_BYTES)
            .ofLongs()
            .setDescription("Disk usage of first level folder in JENKINS_HOME.")
            .setUnit("byte")
            .buildWithCallback(valueObserver -> valueObserver.record(calculateDiskUsageInBytes()));

    }

    private long calculateDiskUsageInBytes() {
        if (this.quickDiskUsagePlugin == null) {
            Jenkins jenkins = Jenkins.get();
            QuickDiskUsagePlugin quickDiskUsagePlugin = jenkins.getPlugin(QuickDiskUsagePlugin.class);
            if (quickDiskUsagePlugin == null) return 0L;
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
}
