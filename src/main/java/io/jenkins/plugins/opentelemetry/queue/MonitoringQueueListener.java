package io.jenkins.plugins.opentelemetry.queue;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import io.jenkins.plugins.opentelemetry.JenkinsOtelPlugin;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.common.Labels;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongSumObserver;
import io.opentelemetry.api.metrics.Meter;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
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

    @PostConstruct
    public void postConstruct() {
        meter.longValueObserverBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_WAITING)
                .setUpdater(longResult -> longResult.observe(this.waitingItemGauge.longValue(), Labels.empty()))
                .setDescription("Number of waiting items in queue")
                .setUnit("1")
                .build();
        meter.longValueObserverBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_BLOCKED)
                .setUpdater(longResult -> longResult.observe(this.blockedItemGauge.longValue(), Labels.empty()))
                .setDescription("Number of blocked items in queue")
                .setUnit("1")
                .build();
        meter.longValueObserverBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_BUILDABLE)
                .setUpdater(longResult -> longResult.observe(this.buildableItemGauge.longValue(), Labels.empty()))
                .setDescription("Number of buildable items in queue")
                .setUnit("1")
                .build();
        leftItemCounter = meter.longCounterBuilder("jenkins.queue.left")
                .setDescription("total count of left items")
                .setUnit("1")
                .build();
        timeInQueueInMillisCounter = meter.longCounterBuilder("jenkins.queue.time_in_millis")
                .setDescription("total time spent in queue by items")
                .setUnit("ms")
                .build();
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
    public void setMeter(@Nonnull JenkinsOtelPlugin jenkinsOtelPlugin) {
        this.meter = jenkinsOtelPlugin.getMeter();
    }
}
