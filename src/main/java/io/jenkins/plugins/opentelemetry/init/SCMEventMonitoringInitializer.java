/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jenkins.YesNoMaybe;
import jenkins.scm.api.SCMEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Capture SCM Events metrics
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class SCMEventMonitoringInitializer implements OtelComponent {

    private final static Logger logger = Logger.getLogger(SCMEventMonitoringInitializer.class.getName());

    @Override
    public void afterSdkInitialized(Meter meter, io.opentelemetry.api.logs.Logger otelLogger, EventEmitter eventEmitter, Tracer tracer, ConfigProperties configProperties) {

            meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_SCM_EVENT_POOL_SIZE)
                .setDescription("Number of threads handling SCM Events")
                .setUnit("1")
                .buildWithCallback(valueObserver -> valueObserver.record(SCMEvent.getEventProcessingMetrics().getPoolSize()));

            meter.upDownCounterBuilder(JenkinsSemanticMetrics.JENKINS_SCM_EVENT_ACTIVE_THREADS)
                .setDescription("Number of threads actively handling SCM Events")
                .setUnit("1")
                .buildWithCallback(valueObserver -> valueObserver.record(SCMEvent.getEventProcessingMetrics().getActiveThreads()));

            meter.upDownCounterBuilder(JenkinsSemanticMetrics.JENKINS_SCM_EVENT_QUEUED_TASKS)
                .setDescription("Number of queued SCM Event tasks")
                .setUnit("1")
                .buildWithCallback(valueObserver -> valueObserver.record(SCMEvent.getEventProcessingMetrics().getQueuedTasks()));

            meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_SCM_EVENT_COMPLETED_TASKS)
                .setDescription("Number of completed SCM Event tasks")
                .setUnit("1")
                .buildWithCallback(valueObserver -> valueObserver.record(SCMEvent.getEventProcessingMetrics().getCompletedTasks()));

        logger.log(Level.FINE, () -> "Start monitoring Jenkins SCM events...");
    }
}
