/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.AbstractOtelComponent;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.metrics.Meter;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.logs.LogEmitter;
import jenkins.YesNoMaybe;
import jenkins.scm.api.SCMEvent;

/**
 * Capture SCM Events metrics
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class SCMEventMonitoringInitializer extends AbstractOtelComponent {

    private final static Logger logger = Logger.getLogger(SCMEventMonitoringInitializer.class.getName());

    @Override
    public void afterSdkInitialized(Meter meter, LogEmitter logEmitter, Tracer tracer, ConfigProperties configProperties) {

        registerInstrument(
            meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_SCM_EVENT_POOL_SIZE)
                .setDescription("Number of threads handling SCM Events")
                .setUnit("1")
                .buildWithCallback(valueObserver -> valueObserver.record(SCMEvent.getEventProcessingMetrics().getPoolSize())));

        registerInstrument(
            meter.upDownCounterBuilder(JenkinsSemanticMetrics.JENKINS_SCM_EVENT_ACTIVE_THREADS)
                .setDescription("Number of threads actively handling SCM Events")
                .setUnit("1")
                .buildWithCallback(valueObserver -> valueObserver.record(SCMEvent.getEventProcessingMetrics().getActiveThreads())));

        registerInstrument(
            meter.upDownCounterBuilder(JenkinsSemanticMetrics.JENKINS_SCM_EVENT_QUEUED_TASKS)
                .setDescription("Number of queued SCM Event tasks")
                .setUnit("1")
                .buildWithCallback(valueObserver -> valueObserver.record(SCMEvent.getEventProcessingMetrics().getQueuedTasks())));

        registerInstrument(
            meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_SCM_EVENT_COMPLETED_TASKS)
                .setDescription("Number of completed SCM Event tasks")
                .setUnit("1")
                .buildWithCallback(valueObserver -> valueObserver.record(SCMEvent.getEventProcessingMetrics().getCompletedTasks())));

        logger.log(Level.FINE, () -> "Start monitoring SCM events");
    }
}
