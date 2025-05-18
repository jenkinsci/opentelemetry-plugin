/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import static io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes.STATUS;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.LoadStatistics;
import hudson.model.Node;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.semconv.CicdMetrics;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.semconv.incubating.CicdIncubatingAttributes;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.jenkins.plugins.opentelemetry.semconv.JenkinsMetrics.*;

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

        final ObservableLongMeasurement queueLength = meter.gaugeBuilder(JENKINS_EXECUTOR_QUEUE).setUnit("${items}").setDescription("Executors queue items").ofLongs().buildObserver();
        final ObservableLongMeasurement totalExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_TOTAL).setUnit("${executors}").setDescription("Total executors").ofLongs().buildObserver();
        final ObservableLongMeasurement nodes = meter.gaugeBuilder(JENKINS_NODE).setUnit("${nodes}").setDescription("Nodes").ofLongs().buildObserver();
        final ObservableLongMeasurement executors = meter.gaugeBuilder(JENKINS_EXECUTOR_COUNT).setUnit("${executors}").setDescription("Count of executors per label").ofLongs().buildObserver();
        ObservableLongMeasurement cicdWorkers = CicdMetrics.newCiCdWorkerCounter(meter);

        // TODO the metrics below should be deprecated in favor of
        //  * `jenkins.executor` metric with the `status` and `label`attributes
        //  * `jenkins.node` metric with the `status` attribute
        //  * `jenkins.executor.total` metric with the `status` attribute
        final ObservableLongMeasurement availableExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_AVAILABLE).setUnit("${executors}").setDescription("Available executors").ofLongs().buildObserver();
        final ObservableLongMeasurement busyExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_BUSY).setUnit("${executors}").setDescription("Busy executors").ofLongs().buildObserver();
        final ObservableLongMeasurement idleExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_IDLE).setUnit("${executors}").setDescription("Idle executors").ofLongs().buildObserver();
        final ObservableLongMeasurement onlineExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_ONLINE).setUnit("${executors}").setDescription("Online executors").ofLongs().buildObserver();
        final ObservableLongMeasurement connectingExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_CONNECTING).setUnit("${executors}").setDescription("Connecting executors").ofLongs().buildObserver();
        final ObservableLongMeasurement definedExecutors = meter.gaugeBuilder(JENKINS_EXECUTOR_DEFINED).setUnit("${executors}").setDescription("Defined executors").ofLongs().buildObserver();

        logger.log(Level.FINER, () -> "Metrics: " + availableExecutors + ", " + busyExecutors + ", " + idleExecutors + ", " + onlineExecutors + ", " + connectingExecutors + ", " + definedExecutors + ", " + queueLength);

        meter.batchCallback(() -> {
            logger.log(Level.FINE, () -> "Recording Jenkins controller executor pool metrics...");
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                logger.log(Level.FINE, "Jenkins instance is null, skipping executor pool metrics recording");
                return;
            }

            // TOTAL EXECUTORS
            AtomicInteger totalExecutorsIdle = new AtomicInteger();
            AtomicInteger totalExecutorsBusy = new AtomicInteger();
            AtomicInteger totalExecutorsOffline = new AtomicInteger();
            AtomicInteger nodeOnline = new AtomicInteger();
            AtomicInteger nodeOffline = new AtomicInteger();

            if (jenkins.getNumExecutors() > 0) {
                nodeOnline.incrementAndGet();
                Optional.ofNullable(jenkins.toComputer())
                    .map(Computer::getExecutors)
                    .ifPresent(e -> e.forEach(executor -> {
                        if (executor.isIdle()) {
                            totalExecutorsIdle.incrementAndGet();
                        } else {
                            totalExecutorsBusy.incrementAndGet();
                        }
                    }));
            }
            jenkins.getNodes().stream().map(Node::toComputer).filter(Objects::nonNull).forEach(node -> {
                if (node.isOnline()) {
                    nodeOnline.incrementAndGet();
                    node.getExecutors()
                        .forEach(executor -> {
                            if (executor.isIdle()) {
                                totalExecutorsIdle.incrementAndGet();
                            } else {
                                totalExecutorsBusy.incrementAndGet();
                            }
                        });
                } else {
                    totalExecutorsOffline.addAndGet(node.countExecutors());
                    nodeOffline.incrementAndGet();
                }
            });

            totalExecutors.record(totalExecutorsBusy.get(), Attributes.of(STATUS, "busy"));
            totalExecutors.record(totalExecutorsIdle.get(), Attributes.of(STATUS, "idle"));
            cicdWorkers.record(totalExecutorsBusy.get(),
                Attributes.of(CicdIncubatingAttributes.CICD_WORKER_STATE, CicdIncubatingAttributes.CicdWorkerStateIncubatingValues.BUSY));
            cicdWorkers.record(totalExecutorsIdle.get(),
                Attributes.of(CicdIncubatingAttributes.CICD_WORKER_STATE, CicdIncubatingAttributes.CicdWorkerStateIncubatingValues.AVAILABLE));
            cicdWorkers.record(totalExecutorsOffline.get(),
                Attributes.of(CicdIncubatingAttributes.CICD_WORKER_STATE, CicdIncubatingAttributes.CicdWorkerStateIncubatingValues.OFFLINE));
            nodes.record(nodeOnline.get(), Attributes.of(STATUS, "online"));
            nodes.record(nodeOffline.get(), Attributes.of(STATUS, "offline"));

            // PER LABEL
            jenkins.getLabels().forEach(label -> {
                LoadStatistics.LoadStatisticsSnapshot loadStatisticsSnapshot = label.loadStatistics.computeSnapshot();
                Attributes attributes = Attributes.of(ExtendedJenkinsAttributes.LABEL, label.getDisplayName());

                executors.record(loadStatisticsSnapshot.getBusyExecutors(), attributes.toBuilder().put(STATUS, "busy").build());
                executors.record(loadStatisticsSnapshot.getIdleExecutors(), attributes.toBuilder().put(STATUS, "idle").build());
                executors.record(loadStatisticsSnapshot.getConnectingExecutors(), attributes.toBuilder().put(STATUS, "connecting").build());
                queueLength.record(loadStatisticsSnapshot.getQueueLength(), attributes);

                // TODO the metrics below should be deprecated in favor of `jenkins.executor` metric with the `status`
                //  and `label`attributes
                availableExecutors.record(loadStatisticsSnapshot.getAvailableExecutors(), attributes);
                busyExecutors.record(loadStatisticsSnapshot.getBusyExecutors(), attributes);
                idleExecutors.record(loadStatisticsSnapshot.getIdleExecutors(), attributes);
                onlineExecutors.record(loadStatisticsSnapshot.getOnlineExecutors(), attributes);
                definedExecutors.record(loadStatisticsSnapshot.getDefinedExecutors(), attributes);
                connectingExecutors.record(loadStatisticsSnapshot.getConnectingExecutors(), attributes);
            });
        }, availableExecutors, busyExecutors, idleExecutors, onlineExecutors, connectingExecutors, definedExecutors, totalExecutors, executors, nodes, queueLength, cicdWorkers);
    }
}
