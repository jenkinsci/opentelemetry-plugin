/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.queue;

import com.cloudbees.simplediskusage.DiskItem;
import com.cloudbees.simplediskusage.QuickDiskUsagePlugin;
import hudson.Extension;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongValueObserver;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitor the Jenkins Build queue
 */
@Extension
public class MonitoringQueueListener extends QueueListener {

    private final static Logger LOGGER = Logger.getLogger(MonitoringQueueListener.class.getName());

    protected Meter meter;

    private final AtomicInteger waitingItemGauge = new AtomicInteger();
    private final AtomicInteger blockedItemGauge = new AtomicInteger();
    private final AtomicInteger buildableItemGauge = new AtomicInteger();
    private LongCounter leftItemCounter;
    private LongCounter timeInQueueInMillisCounter;
    private LongValueObserver diskObserver;

    @PostConstruct
    public void postConstruct() {
        meter.longValueObserverBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_WAITING)
                .setUpdater(longResult -> longResult.observe(this.getUnblockedItemsSize(), Labels.empty()))
                .setDescription("Number of waiting items in queue")
                .setUnit("1")
                .build();
        meter.longValueObserverBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_BLOCKED)
                .setUpdater(longResult -> longResult.observe(this.blockedItemGauge.longValue(), Labels.empty()))
                .setDescription("Number of blocked items in queue")
                .setUnit("1")
                .build();
        meter.longValueObserverBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_BUILDABLE)
                .setUpdater(longResult -> longResult.observe(this.getBuildableItemsSize(), Labels.empty()))
                .setDescription("Number of buildable items in queue")
                .setUnit("1")
                .build();
        leftItemCounter = meter.longCounterBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_LEFT)
                .setDescription("Total count of left items")
                .setUnit("1")
                .build();
        timeInQueueInMillisCounter = meter.longCounterBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_TIME_SPENT_MILLIS)
                .setDescription("Total time spent in queue by items")
                .setUnit("ms")
                .build();
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            QuickDiskUsagePlugin diskUsagePlugin = jenkins.getPlugin(QuickDiskUsagePlugin.class);
            if (diskUsagePlugin != null) {
                diskObserver = meter.longValueObserverBuilder(JenkinsSemanticMetrics.JENKINS_DISK_USAGE)
                        .setDescription("Disk usage of first level folder in JENKINS_HOME.")
                        .setUnit("byte")
                        .setUpdater(result -> result.observe(calculateDiskUsage(diskUsagePlugin), Labels.empty()))
                        .build();
            }
        }
    }

    private long calculateDiskUsage(QuickDiskUsagePlugin diskUsagePlugin) {
        try {
            DiskItem disk = diskUsagePlugin.getDirectoriesUsages()
                    .stream()
                    .filter(x -> x.getDisplayName().equals("JENKINS_HOME"))
                    .findFirst()
                    .orElse(null);
            if (disk != null) {
                return disk.getUsage() * 1024;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e, () -> "Exception invoking `diskUsagePlugin.getDirectoriesUsages()`");
        }
        return 0;
    }

    private long getUnblockedItemsSize() {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return 0;
        }
        final Queue queue = jenkins.getQueue();
        if (queue == null) {
            return 0;
        }
        return queue.getUnblockedItems().size();
    }

    private long getBuildableItemsSize() {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return 0;
        }
        final Queue queue = jenkins.getQueue();
        if (queue == null) {
            return 0;
        }
        return queue.getBuildableItems().size();
    }

    @Override
    public void onEnterWaiting(Queue.WaitingItem wi) {
        this.waitingItemGauge.incrementAndGet();
    }

    @Override
    public void onLeaveWaiting(Queue.WaitingItem wi) {
        this.waitingItemGauge.decrementAndGet();
    }

    @Override
    public void onEnterBlocked(Queue.BlockedItem bi) {
        this.blockedItemGauge.incrementAndGet();
    }

    @Override
    public void onLeaveBlocked(Queue.BlockedItem bi) {
        this.blockedItemGauge.decrementAndGet();
    }

    @Override
    public void onEnterBuildable(Queue.BuildableItem bi) {
        this.buildableItemGauge.incrementAndGet();
    }

    @Override
    public void onLeaveBuildable(Queue.BuildableItem bi) {
        this.buildableItemGauge.decrementAndGet();
    }

    @Override
    public void onLeft(Queue.LeftItem li) {
        this.leftItemCounter.add(1);
        this.timeInQueueInMillisCounter.add(System.currentTimeMillis() - li.getInQueueSince());
        LOGGER.log(Level.FINE, () -> "onLeft(): " + li.toString());
    }

    /**
     * Jenkins doesn't support {@link com.google.inject.Provides} so we manually wire dependencies :-(
     */
    @Inject
    public void setMeter(@Nonnull OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.meter = openTelemetrySdkProvider.getMeter();
    }
}
