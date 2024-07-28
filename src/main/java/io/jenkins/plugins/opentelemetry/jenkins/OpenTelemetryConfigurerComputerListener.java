/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.jenkins;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OpenTelemetryConfiguration;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FIXME support specifying which Computer should be instrumented with OTel.
 */
@Extension(ordinal = Integer.MAX_VALUE)
public class OpenTelemetryConfigurerComputerListener extends ComputerListener {

    private static final Logger logger = Logger.getLogger(OpenTelemetryConfigurerComputerListener.class.getName());

    @Inject
    JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration;

    @Override
    public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener) throws InterruptedException {
        try {
            OpenTelemetryConfiguration openTelemetryConfiguration = jenkinsOpenTelemetryPluginConfiguration.toOpenTelemetryConfiguration();

            Map<String, String> otelSdkProperties = openTelemetryConfiguration.toOpenTelemetryProperties();
            Map<String, String> otelSdkResourceProperties = openTelemetryConfiguration.toOpenTelemetryResourceAsMap();
            otelSdkResourceProperties.put(JenkinsOtelSemanticAttributes.JENKINS_COMPUTER_NAME.getKey(), c.getName());
            Object result = channel.call(new OpenTelemetryConfigurerMasterToSlaveCallable(otelSdkProperties, otelSdkResourceProperties));
            logger.log(Level.FINER, "OpenTelemetry configured on computer " + c.getName() + Optional.ofNullable(result).map(r -> ": " + r).orElse(""));
        } catch (IOException | RuntimeException e) {
            logger.log(Level.INFO, "Failure to configure OpenTelemetry on computer " + c.getName(), e);
        }
    }
}
