/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Node;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class MonitoringCloudListener extends CloudProvisioningListener {
    private final static Logger LOGGER = Logger.getLogger(MonitoringCloudListener.class.getName());

    protected Meter meter;

    private final AtomicInteger failureCloudGauge = new AtomicInteger();
    private final AtomicInteger totalCloudGauge = new AtomicInteger();

    @PostConstruct
    public void postConstruct() {
        meter.longValueObserverBuilder(JenkinsSemanticMetrics.JENKINS_CLOUD_AGENTS_FAILURE)
                .setUpdater(longResult -> longResult.observe(this.failureCloudGauge.longValue(), Labels.empty()))
                .setDescription("Number of failed cloud agents when provisioning")
                .setUnit("1")
                .build();
        meter.longValueObserverBuilder(JenkinsSemanticMetrics.JENKINS_CLOUD_AGENTS_COMPLETED)
            .setUpdater(longResult -> longResult.observe(this.totalCloudGauge.longValue(), Labels.empty()))
            .setDescription("Number of provisioned cloud agents")
            .setUnit("1")
            .build();
    }

    @Override
    public void onFailure(NodeProvisioner.PlannedNode plannedNode, Throwable t) {
        failureCloudGauge.incrementAndGet();
        LOGGER.log(Level.FINE, () -> "onFailure(" + plannedNode + ")");
    }

    @Override
    public void onRollback(@NonNull NodeProvisioner.PlannedNode plannedNode, @NonNull Node node,
                           @NonNull Throwable t) {
        failureCloudGauge.incrementAndGet();
        LOGGER.log(Level.FINE, () -> "onRollback(" + plannedNode + ")");
    }

    @Override
    public void onComplete(NodeProvisioner.PlannedNode plannedNode, Node node) {
        totalCloudGauge.incrementAndGet();
        LOGGER.log(Level.FINE, () -> "onComplete(" + plannedNode + ")");
    }

    /**
     * Jenkins doesn't support {@link com.google.inject.Provides} so we manually wire dependencies :-(
     */
    @Inject
    public void setMeter(@Nonnull OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.meter = openTelemetrySdkProvider.getMeter();
    }
}
