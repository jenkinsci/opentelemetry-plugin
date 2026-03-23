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
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsMetrics;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import jenkins.YesNoMaybe;

/**
 * Cloud provisioning listener that reports successful and failed cloud-agent provisioning metrics.
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class MonitoringCloudListener extends CloudProvisioningListener implements OpenTelemetryLifecycleListener {
    private static final Logger LOGGER = Logger.getLogger(MonitoringCloudListener.class.getName());

    private LongCounter failureCloudCounter;
    private LongCounter totalCloudCount;

    @Inject
    private JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    /** Initializes cloud-agent metrics after OpenTelemetry lifecycle configuration is available. */
    @PostConstruct
    public void postConstruct() {
        Meter meter = jenkinsControllerOpenTelemetry.getDefaultMeter();
        LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins controller cloud agent provisioning...");

        failureCloudCounter = meter.counterBuilder(JenkinsMetrics.JENKINS_CLOUD_AGENTS_FAILURE)
                .setDescription("Number of failed cloud agents when provisioning")
                .setUnit("{agents}")
                .build();
        totalCloudCount = meter.counterBuilder(JenkinsMetrics.JENKINS_CLOUD_AGENTS_COMPLETED)
                .setDescription("Number of provisioned cloud agents")
                .setUnit("{agents}")
                .build();
    }

    /**
     * Records a failed cloud provisioning attempt.
     *
     * @param plannedNode failed planned node
     * @param t failure cause
     */
    @Override
    public void onFailure(NodeProvisioner.PlannedNode plannedNode, Throwable t) {
        failureCloudCounter.add(1);
        LOGGER.log(Level.FINE, () -> "onFailure(" + plannedNode + ")");
    }

    /**
     * Records a provisioning rollback as a failed cloud-agent provisioning event.
     *
     * @param plannedNode planned node being rolled back
     * @param node created node instance
     * @param t rollback cause
     */
    @Override
    public void onRollback(@NonNull NodeProvisioner.PlannedNode plannedNode, @NonNull Node node, @NonNull Throwable t) {
        failureCloudCounter.add(1);
        LOGGER.log(Level.FINE, () -> "onRollback(" + plannedNode + ")");
    }

    /**
     * Records a successful cloud provisioning completion event.
     *
     * @param plannedNode completed planned node
     * @param node provisioned node instance
     */
    @Override
    public void onComplete(NodeProvisioner.PlannedNode plannedNode, Node node) {
        totalCloudCount.add(1);
        LOGGER.log(Level.FINE, () -> "onComplete(" + plannedNode + ")");
    }
}
