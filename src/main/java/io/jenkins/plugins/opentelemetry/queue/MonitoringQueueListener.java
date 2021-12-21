/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.queue;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
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

    private final AtomicInteger blockedItemGauge = new AtomicInteger();
    private LongCounter leftItemCounter;
    private LongCounter timeInQueueInMillisCounter;

    @PostConstruct
    public void postConstruct() {
        meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_WAITING)
            .ofLongs()
            .setDescription("Number of waiting items in queue")
            .setUnit("1")
            .buildWithCallback(valueObserver -> valueObserver.record(this.getUnblockedItemsSize()));
        meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_BLOCKED)
            .ofLongs()
            .setDescription("Number of blocked items in queue")
            .setUnit("1")
            .buildWithCallback(valueObserver -> valueObserver.record(this.blockedItemGauge.longValue()));
        meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_BUILDABLE)
            .ofLongs()
            .setDescription("Number of buildable items in queue")
            .setUnit("1")
            .buildWithCallback(valueObserver -> valueObserver.record(this.getBuildableItemsSize()));
        leftItemCounter = meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_LEFT)
            .setDescription("Total count of left items")
            .setUnit("1")
            .build();
        timeInQueueInMillisCounter = meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_TIME_SPENT_MILLIS)
            .setDescription("Total time spent in queue by items")
            .setUnit("ms")
            .build();
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
    public void onEnterBlocked(Queue.BlockedItem bi) {
        this.blockedItemGauge.incrementAndGet();
    }

    @Override
    public void onLeaveBlocked(Queue.BlockedItem bi) {
        this.blockedItemGauge.decrementAndGet();
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
