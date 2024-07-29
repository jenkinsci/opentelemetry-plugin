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
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO support specifying which Computer should be instrumented with OTel.
 */
@Extension(ordinal = Integer.MAX_VALUE)
public class OpenTelemetryConfigurerComputerListener extends ComputerListener implements OpenTelemetryLifecycleListener {

    private static final Logger logger = Logger.getLogger(OpenTelemetryConfigurerComputerListener.class.getName());

    final AtomicBoolean buildAgentsInstrumentationEnabled = new AtomicBoolean(false);

    JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration;

    @Override
    public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener) throws InterruptedException {
        if (buildAgentsInstrumentationEnabled.get()) {
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

    @Inject
    public void setJenkinsOpenTelemetryPluginConfiguration(JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration) {
        this.jenkinsOpenTelemetryPluginConfiguration = jenkinsOpenTelemetryPluginConfiguration;
        ConfigProperties configProperties = this.jenkinsOpenTelemetryPluginConfiguration.getConfigProperties();
        this.buildAgentsInstrumentationEnabled.set(configProperties.getBoolean(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_AGENTS_ENABLED, false));
    }

    @Override
    public void afterConfiguration(ConfigProperties configProperties) {
        this.buildAgentsInstrumentationEnabled.set(configProperties.getBoolean(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_AGENTS_ENABLED, false));
    }
}
