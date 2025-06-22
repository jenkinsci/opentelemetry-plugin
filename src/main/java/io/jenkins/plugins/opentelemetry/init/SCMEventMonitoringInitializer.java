/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsMetrics;
import io.opentelemetry.api.metrics.Meter;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import jenkins.YesNoMaybe;
import jenkins.scm.api.SCMEvent;

/**
 * Capture SCM Events metrics
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class SCMEventMonitoringInitializer implements OpenTelemetryLifecycleListener {

    private static final Logger logger = Logger.getLogger(SCMEventMonitoringInitializer.class.getName());

    @Inject
    protected JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    @PostConstruct
    public void postConstruct() {
        logger.log(Level.FINE, () -> "Start monitoring Jenkins controller SCM events...");

        Meter meter = Objects.requireNonNull(jenkinsControllerOpenTelemetry).getDefaultMeter();
        meter.counterBuilder(JenkinsMetrics.JENKINS_SCM_EVENT_POOL_SIZE)
                .setDescription("Number of threads handling SCM Events")
                .setUnit("{events}")
                .buildWithCallback(valueObserver -> valueObserver.record(
                        SCMEvent.getEventProcessingMetrics().getPoolSize()));

        meter.upDownCounterBuilder(JenkinsMetrics.JENKINS_SCM_EVENT_ACTIVE_THREADS)
                .setDescription("Number of threads actively handling SCM Events")
                .setUnit("{threads}")
                .buildWithCallback(valueObserver -> valueObserver.record(
                        SCMEvent.getEventProcessingMetrics().getActiveThreads()));

        meter.upDownCounterBuilder(JenkinsMetrics.JENKINS_SCM_EVENT_QUEUED_TASKS)
                .setDescription("Number of queued SCM Event tasks")
                .setUnit("{tasks}")
                .buildWithCallback(valueObserver -> valueObserver.record(
                        SCMEvent.getEventProcessingMetrics().getQueuedTasks()));

        meter.counterBuilder(JenkinsMetrics.JENKINS_SCM_EVENT_COMPLETED_TASKS)
                .setDescription("Number of completed SCM Event tasks")
                .setUnit("{tasks}")
                .buildWithCallback(valueObserver -> valueObserver.record(
                        SCMEvent.getEventProcessingMetrics().getCompletedTasks()));
    }
}
