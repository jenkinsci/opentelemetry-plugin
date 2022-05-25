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
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.logs.LogEmitter;
import jenkins.YesNoMaybe;

import java.util.logging.Level;
import java.util.logging.Logger;

@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class MonitoringCloudListener extends CloudProvisioningListener implements OtelComponent {
    private final static Logger LOGGER = Logger.getLogger(MonitoringCloudListener.class.getName());

    private LongCounter failureCloudCounter;
    private LongCounter totalCloudCount;

    @Override
    public void afterSdkInitialized(Meter meter, LogEmitter logEmitter, Tracer tracer, ConfigProperties configProperties) {
        failureCloudCounter = meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_CLOUD_AGENTS_FAILURE)
            .setDescription("Number of failed cloud agents when provisioning")
            .setUnit("1")
            .build();
        totalCloudCount = meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_CLOUD_AGENTS_COMPLETED)
            .setDescription("Number of provisioned cloud agents")
            .setUnit("1")
            .build();

        LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins cloud agent provisioning...");
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

    @Override
    public void beforeSdkShutdown() {

    }
}
