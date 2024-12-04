/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.queue;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics.JENKINS_QUEUE;

/**
 * Monitor the Jenkins Build queue
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class MonitoringQueueListener extends QueueListener implements OpenTelemetryLifecycleListener {

    private final static Logger LOGGER = Logger.getLogger(MonitoringQueueListener.class.getName());

    private LongCounter leftItemCounter;
    private LongCounter timeInQueueInMillisCounter;
    @Inject
    private JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;
    private final AtomicBoolean traceContextPropagationEnabled = new AtomicBoolean(false);

    @Override
    public void afterConfiguration(ConfigProperties configProperties) {
        traceContextPropagationEnabled.set(configProperties.getBoolean(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_REMOTE_SPAN_ENABLED, false));
    }

    @PostConstruct
    public void postConstruct() {
        LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins queue...");

        Meter meter = jenkinsControllerOpenTelemetry.getDefaultMeter();

        final ObservableLongMeasurement queueItems = meter.gaugeBuilder(JENKINS_QUEUE)
            .ofLongs()
            .setDescription("Number of tasks in the queue")
            .setUnit("${tasks}")
            .buildObserver();
        // should be deprecated in favor of "jenkins.queue" metric with status attribute
        final ObservableLongMeasurement queueWaitingItems = meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_WAITING)
            .ofLongs()
            .setDescription("Number of tasks in the queue with the status 'waiting', 'buildable' or 'pending'")
            .setUnit("{tasks}")
            .buildObserver();
        // should be deprecated in favor of "jenkins.queue" metric with status attribute
        final ObservableLongMeasurement queueBlockedItems = meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_BLOCKED)
            .ofLongs()
            .setDescription("Number of blocked tasks in the queue. Note that waiting for an executor to be available is not a reason to be counted as blocked")
            .setUnit("{tasks}")
            .buildObserver();
        // should be deprecated in favor of "jenkins.queue" metric with status attribute
        final ObservableLongMeasurement queueBuildableItems = meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_BUILDABLE)
            .ofLongs()
            .setDescription("Number of tasks in the queue with the status 'buildable' or 'pending'")
            .setUnit("{tasks}")
            .buildObserver();

        meter.batchCallback(() -> {
            LOGGER.log(Level.FINE, () -> "Recording Jenkins queue metrics...");

            Optional<Queue> queue = Optional.ofNullable(Jenkins.getInstanceOrNull()).map(Jenkins::getQueue);
            queue.map(Queue::getItems)
                .ifPresent(items -> {
                    AtomicInteger blocked = new AtomicInteger();
                    AtomicInteger buildable = new AtomicInteger();
                    AtomicInteger left = new AtomicInteger();
                    AtomicInteger stuck = new AtomicInteger();
                    AtomicInteger unknown = new AtomicInteger();
                    AtomicInteger waiting = new AtomicInteger();
                    Arrays.stream(items).forEach(item -> {
                        if (item instanceof Queue.BlockedItem) {
                            blocked.incrementAndGet();
                        } else if (item instanceof Queue.BuildableItem) {
                            if (item.isStuck()) {
                                // buildable but here for too long
                                stuck.incrementAndGet();
                            } else {
                                buildable.incrementAndGet();
                            }
                        } else if (item instanceof Queue.WaitingItem) {
                            waiting.incrementAndGet();
                        } else if (item instanceof Queue.LeftItem) {
                            left.incrementAndGet();
                        } else {
                            LOGGER.log(Level.INFO, () -> "Unknown item: " + item + " - class=" + item.getClass());
                            unknown.incrementAndGet();
                        }
                    });
                    queueItems.record(blocked.get(), Attributes.of(JenkinsOtelSemanticAttributes.STATUS, "blocked"));
                    queueBlockedItems.record(blocked.get());
                    queueItems.record(buildable.get(), Attributes.of(JenkinsOtelSemanticAttributes.STATUS, "buildable"));
                    queueBuildableItems.record(buildable.get());
                    queueItems.record(stuck.get(), Attributes.of(JenkinsOtelSemanticAttributes.STATUS, "stuck"));
                    if (unknown.get() > 0) {
                        queueItems.record(unknown.get(), Attributes.of(JenkinsOtelSemanticAttributes.STATUS, "unknown"));
                    }
                    queueItems.record(waiting.get(), Attributes.of(JenkinsOtelSemanticAttributes.STATUS, "waiting"));
                    queueWaitingItems.record(waiting.get());
                });
        }, queueItems, queueWaitingItems, queueBlockedItems, queueBuildableItems);

        leftItemCounter = meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_LEFT)
            .setDescription("Total count of tasks that have been processed")
            .setUnit("{tasks}")
            .build();
        timeInQueueInMillisCounter = meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_QUEUE_TIME_SPENT_MILLIS)
            .setDescription("Total time spent in queue by the tasks that have been processed")
            .setUnit("ms")
            .build();
    }

    @Override
    public void onLeft(Queue.LeftItem li) {
        this.leftItemCounter.add(1);
        this.timeInQueueInMillisCounter.add(System.currentTimeMillis() - li.getInQueueSince());
        LOGGER.log(Level.FINE, () -> "onLeft(): " + li);
    }

    @Override
    public void onEnterWaiting(Queue.WaitingItem wi) {
        if (traceContextPropagationEnabled.get()) {
            Span span = Span.fromContextOrNull(Context.current());
            if (span != null) {
                SpanContext spanContext = span.getSpanContext();
                wi.addAction(new RemoteSpanAction(spanContext.getTraceId(), spanContext.getSpanId(), spanContext.getTraceFlags().asByte(), spanContext.getTraceState().asMap()));
                LOGGER.log(Level.FINE, () -> "attach RemoteSpanAction to " + wi);
            }
        }
    }
}
