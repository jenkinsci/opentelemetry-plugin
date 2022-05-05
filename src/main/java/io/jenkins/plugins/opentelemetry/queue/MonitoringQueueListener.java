/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.queue;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.logs.LogEmitter;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitor the Jenkins Build queue
 */
@Extension
public class MonitoringQueueListener extends QueueListener implements OtelComponent {

    private final static Logger LOGGER = Logger.getLogger(MonitoringQueueListener.class.getName());

    private OtelComponent.State state = new OtelComponent.State();

    private final AtomicInteger blockedItemGauge = new AtomicInteger();
    private LongCounter leftItemCounter;
    private LongCounter timeInQueueInMillisCounter;

    @Override
    public void afterSdkInitialized(Meter meter, LogEmitter logEmitter, Tracer tracer, ConfigProperties configProperties) {

        state.registerInstrument(
            meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_WAITING)
                .ofLongs()
                .setDescription("Number of tasks in the queue with the status 'waiting', 'buildable' or 'pending'")
                .setUnit("1")
                .buildWithCallback(valueObserver -> valueObserver.record((long)
                    Optional.ofNullable(Jenkins.getInstanceOrNull()).map(j -> j.getQueue()).
                        map(q -> q.getUnblockedItems().size()).orElse(0))));

        state.registerInstrument(
            meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_BLOCKED)
                .ofLongs()
                .setDescription("Number of blocked tasks in the queue. Note that waiting for an executor to be available is not a reason to be counted as blocked")
                .setUnit("1")
                .buildWithCallback(valueObserver -> valueObserver.record(this.blockedItemGauge.longValue())));

        state.registerInstrument(
            meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_BUILDABLE)
                .ofLongs()
                .setDescription("Number of tasks in the queue with the status 'buildable' or 'pending'")
                .setUnit("1")
                .buildWithCallback(valueObserver -> valueObserver.record((long)
                    Optional.ofNullable(Jenkins.getInstanceOrNull()).map(j -> j.getQueue()).
                        map(q -> q.countBuildableItems()).orElse(0))));

        leftItemCounter = meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_LEFT)
            .setDescription("Total count of tasks that have been processed")
            .setUnit("1")
            .build();
        timeInQueueInMillisCounter = meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_TIME_SPENT_MILLIS)
            .setDescription("Total time spent in queue by the tasks that have been processed")
            .setUnit("ms")
            .build();
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
        LOGGER.log(Level.FINE, () -> "onLeft(): " + li);
    }

    @Override
    public void beforeSdkShutdown() {
        state.closeInstruments();
    }
}
