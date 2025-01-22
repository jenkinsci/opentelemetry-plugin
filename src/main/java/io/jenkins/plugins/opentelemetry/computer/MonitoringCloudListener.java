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
import jenkins.YesNoMaybe;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class MonitoringCloudListener extends CloudProvisioningListener implements OpenTelemetryLifecycleListener {
    private final static Logger LOGGER = Logger.getLogger(MonitoringCloudListener.class.getName());

    private LongCounter failureCloudCounter;
    private LongCounter totalCloudCount;

    @Inject
    private JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

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
}
