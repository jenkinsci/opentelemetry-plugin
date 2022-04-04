/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.metrics.Meter;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import jenkins.YesNoMaybe;
import jenkins.scm.api.SCMEvent;

/**
 * Capture SCM Events metrics
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class SCMEventMonitoringInitializer {

    private final static Logger LOGGER = Logger.getLogger(SCMEventMonitoringInitializer.class.getName());

    protected Meter meter;

    @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
    public void initialize() {
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

        LOGGER.log(Level.FINE, () -> "Start monitoring SCM events");
    }

    /**
     * Jenkins doesn't support {@link com.google.inject.Provides} so we manually wire dependencies :-(
     */
    @Inject
    public void setMeter(@Nonnull OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.meter = openTelemetrySdkProvider.getMeter();
    }
}
