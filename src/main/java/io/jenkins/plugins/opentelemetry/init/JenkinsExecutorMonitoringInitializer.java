/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import hudson.model.LoadStatistics;
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.events.EventLogger;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;

import static io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics.*;

@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class JenkinsExecutorMonitoringInitializer implements OtelComponent {

    @Override
    public void afterSdkInitialized(Meter meter, LoggerProvider loggerProvider, EventLogger eventLogger, Tracer tracer, ConfigProperties configProperties) {

        final ObservableLongMeasurement availableExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_AVAILABLE).setUnit("1").setDescription("Available executors").ofLongs().buildObserver();
        final ObservableLongMeasurement busyExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_BUSY).setUnit("1").setDescription("Busy executors").ofLongs().buildObserver();
        final ObservableLongMeasurement idleExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_IDLE).setUnit("1").setDescription("Idle executors").ofLongs().buildObserver();
        final ObservableLongMeasurement onlineExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_ONLINE).setUnit("1").setDescription("Online executors").ofLongs().buildObserver();
        final ObservableLongMeasurement connectingExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_CONNECTING).setUnit("1").setDescription("Connecting executors").ofLongs().buildObserver();
        final ObservableLongMeasurement definedExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_DEFINED).setUnit("1").setDescription("Defined executors").ofLongs().buildObserver();
        final ObservableLongMeasurement queueLength = meter.gaugeBuilder(JENKINS_EXECUTOR_QUEUE).setUnit("1").setDescription("Defined executors").ofLongs().buildObserver();

        meter.batchCallback(() -> {
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
