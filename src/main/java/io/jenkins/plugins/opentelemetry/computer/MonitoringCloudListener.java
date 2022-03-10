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
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class MonitoringCloudListener extends CloudProvisioningListener {
    private final static Logger LOGGER = Logger.getLogger(MonitoringCloudListener.class.getName());

    protected Meter meter;

    private LongCounter failureCloudCounter;
    private LongCounter totalCloudCount;

    @PostConstruct
    public void postConstruct() {
        failureCloudCounter = meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_CLOUD_AGENTS_FAILURE)
            .setDescription("Number of failed cloud agents when provisioning")
            .setUnit("1")
            .build();
        totalCloudCount = meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_CLOUD_AGENTS_COMPLETED)
            .setDescription("Number of provisioned cloud agents")
            .setUnit("1")
            .build();
    }

    @Override
    public void onFailure(NodeProvisioner.PlannedNode plannedNode, Throwable t) {
        failureCloudCounter.add(1);
        LOGGER.log(Level.FINE, () -> "onFailure(" + plannedNode + ")");
    }

    @Override
    public void onRollback(@NonNull NodeProvisioner.PlannedNode plannedNode, @NonNull Node node,
                           @NonNull Throwable t) {
        failureCloudCounter.add(1);
        LOGGER.log(Level.FINE, () -> "onRollback(" + plannedNode + ")");
    }

    @Override
    public void onComplete(NodeProvisioner.PlannedNode plannedNode, Node node) {
        totalCloudCount.add(1);
        LOGGER.log(Level.FINE, () -> "onComplete(" + plannedNode + ")");
    }

    /**
     * Jenkins doesn't support {@link com.google.inject.Provides} so we manually wire dependencies :-(
     */
    @Inject
    public void setMeter(@NonNull OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.meter = openTelemetrySdkProvider.getMeter();
    }
}
