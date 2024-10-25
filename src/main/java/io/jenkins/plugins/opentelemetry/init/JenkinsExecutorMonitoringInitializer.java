/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import hudson.model.LoadStatistics;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics.*;

@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class JenkinsExecutorMonitoringInitializer implements OpenTelemetryLifecycleListener {

    private static final Logger logger = Logger.getLogger(JenkinsExecutorMonitoringInitializer.class.getName());

    @Inject
    JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    public JenkinsExecutorMonitoringInitializer() {
        logger.log(Level.FINE, () -> "JenkinsExecutorMonitoringInitializer constructor");
    }

    @PostConstruct
    public void postConstruct() {

        logger.log(Level.FINE, () -> "Start monitoring Jenkins controller executor pool...");

        Meter meter = Objects.requireNonNull(jenkinsControllerOpenTelemetry).getDefaultMeter();
        final ObservableLongMeasurement availableExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_AVAILABLE).setUnit("${executors}").setDescription("Available executors").ofLongs().buildObserver();
        final ObservableLongMeasurement busyExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_BUSY).setUnit("${executors}").setDescription("Busy executors").ofLongs().buildObserver();
        final ObservableLongMeasurement idleExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_IDLE).setUnit("${executors}").setDescription("Idle executors").ofLongs().buildObserver();
        final ObservableLongMeasurement onlineExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_ONLINE).setUnit("${executors}").setDescription("Online executors").ofLongs().buildObserver();
        final ObservableLongMeasurement connectingExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_CONNECTING).setUnit("${executors}").setDescription("Connecting executors").ofLongs().buildObserver();
        final ObservableLongMeasurement definedExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_DEFINED).setUnit("${executors}").setDescription("Defined executors").ofLongs().buildObserver();
        final ObservableLongMeasurement queueLength = meter.gaugeBuilder(JENKINS_EXECUTOR_QUEUE).setUnit("${items}").setDescription("Executors queue items").ofLongs().buildObserver();
        logger.log(Level.FINER, () -> "Metrics: " + availableExecutors + ", " + busyExecutors + ", " + idleExecutors + ", " + onlineExecutors + ", " + connectingExecutors + ", " + definedExecutors + ", " + queueLength);

        meter.batchCallback(() -> {
            logger.log(Level.FINE, () -> "Recording Jenkins controller executor pool metrics...");
            logger.log(Level.FINER, () -> "Metrics: " + availableExecutors + ", " + busyExecutors + ", " + idleExecutors + ", " + onlineExecutors + ", " + connectingExecutors + ", " + definedExecutors + ", " + queueLength);
            Jenkins jenkins = Jenkins.get();
            jenkins.getLabels().forEach(label -> {
                LoadStatistics.LoadStatisticsSnapshot loadStatisticsSnapshot = label.loadStatistics.computeSnapshot();
                Attributes attributes = Attributes.of(AttributeKey.stringKey("label"), label.getDisplayName());
                availableExecutors.record(loadStatisticsSnapshot.getAvailableExecutors(), attributes);
                busyExecutors.record(loadStatisticsSnapshot.getBusyExecutors(), attributes);
                idleExecutors.record(loadStatisticsSnapshot.getIdleExecutors(), attributes);
                onlineExecutors.record(loadStatisticsSnapshot.getOnlineExecutors(), attributes);
                definedExecutors.record(loadStatisticsSnapshot.getDefinedExecutors(), attributes);
                connectingExecutors.record(loadStatisticsSnapshot.getConnectingExecutors(), attributes);
                queueLength.record(loadStatisticsSnapshot.getQueueLength(), attributes);
            });
        }, availableExecutors, busyExecutors, idleExecutors, onlineExecutors, connectingExecutors, definedExecutors, queueLength);
    }
}
