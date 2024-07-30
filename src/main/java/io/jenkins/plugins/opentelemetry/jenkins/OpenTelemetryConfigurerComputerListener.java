/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.jenkins;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerListener;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OpenTelemetryConfiguration;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.opentelemetry.GlobalOpenTelemetrySdk;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO make update of jenkins build agents an async process
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
                logger.log(Level.FINE, () -> "OpenTelemetry configured on computer " + c.getName() + Optional.ofNullable(result).map(r -> ": " + r).orElse(""));
            } catch (IOException | RuntimeException e) {
                logger.log(Level.INFO, e, () -> "Failure to configure OpenTelemetry on computer " + c.getName());
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
        boolean buildAgentsInstrumentationEnabledPreviousVersion = buildAgentsInstrumentationEnabled.get();
        this.buildAgentsInstrumentationEnabled.set(configProperties.getBoolean(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_AGENTS_ENABLED, false));

        // Update the configuration of the Jenkins build agents
        OpenTelemetryConfiguration openTelemetryConfiguration = jenkinsOpenTelemetryPluginConfiguration.toOpenTelemetryConfiguration();

        Map<String, String> otelSdkProperties = openTelemetryConfiguration.toOpenTelemetryProperties();
        Map<String, String> otelSdkResourceProperties = openTelemetryConfiguration.toOpenTelemetryResourceAsMap();

        if (!buildAgentsInstrumentationEnabledPreviousVersion && !this.buildAgentsInstrumentationEnabled.get()) {
            // build agent instrumentation remains disabled, don't do anything
            logger.log(Level.FINE, () -> "Build agent instrumentation remains disabled, no need to update OpenTelemetry configuration on jenkins build agents");
        } else {
            Arrays.stream(Jenkins.get().getComputers()).forEach(computer -> {
                Node node = computer.getNode();
                logger.log(Level.FINE, () ->
                    "Evaluate computer.name: '" + computer.getName() +
                        "', node: " + Optional.ofNullable(node).map(n -> n.getNodeName() + " / " + n.getClass().getName()));

                if (node instanceof Jenkins) {
                    // skip Jenkins controller
                } else if (computer.isOnline()) {
                    VirtualChannel channel = computer.getChannel();
                    if (channel == null) {
                        logger.log(Level.FINE, () -> "Failure to update OpenTelemetry configuration for computer/build-agent '" + computer.getName() + "' as its channel is null");
                    } else {
                        Map<String, String> buildAgentOtelSdkProperties;
                        Map<String, String> buildAgentOtelSdkResourceProperties;
                        if (this.buildAgentsInstrumentationEnabled.get()) {
                            buildAgentOtelSdkProperties = new HashMap<>(otelSdkProperties);
                            buildAgentOtelSdkResourceProperties = new HashMap<>(otelSdkResourceProperties);
                            buildAgentOtelSdkResourceProperties.put(JenkinsOtelSemanticAttributes.JENKINS_COMPUTER_NAME.getKey(), computer.getName());
                        } else {
                            buildAgentOtelSdkProperties = Collections.emptyMap();
                            buildAgentOtelSdkResourceProperties = Collections.emptyMap();
                        }
                        OpenTelemetryConfigurerMasterToSlaveCallable callable;
                        callable = new OpenTelemetryConfigurerMasterToSlaveCallable(buildAgentOtelSdkProperties, buildAgentOtelSdkResourceProperties);

                        try {
                            logger.log(Level.FINE, () -> "Updating OpenTelemetry configuration for computer/build-agent '" + computer.getName() + "'...");
                            Object result = channel.call(callable);

                        } catch (IOException | RuntimeException | InterruptedException e) {
                            logger.log(Level.INFO, e, () -> "Failure to update OpenTelemetry configuration for computer/build-agent '" + computer.getName() + "'");
                        }
                    }
                } else {
                    logger.log(Level.FINE, () -> "Failure to update OpenTelemetry configuration for computer/build-agent '" + computer.getName() + "' as it is offline");
                }
            });
        }
    }

    public static class OpenTelemetryConfigurerMasterToSlaveCallable extends MasterToSlaveCallable<Object, RuntimeException> {
        static final Logger logger = Logger.getLogger(OpenTelemetryConfigurerMasterToSlaveCallable.class.getName());

        final Map<String, String> otelSdkConfigurationProperties;
        final Map<String, String> otelSdkResource;

        public OpenTelemetryConfigurerMasterToSlaveCallable(Map<String, String> otelSdkConfigurationProperties, Map<String, String> otelSdkResource) {
            this.otelSdkConfigurationProperties = otelSdkConfigurationProperties;
            this.otelSdkResource = otelSdkResource;
        }

        @Override
        public Object call() throws RuntimeException {
            logger.log(Level.INFO, () -> "Configure OpenTelemetry SDK with properties: " + otelSdkConfigurationProperties + ", resource:" + otelSdkResource);
            GlobalOpenTelemetrySdk.configure(otelSdkConfigurationProperties, otelSdkResource, true);
            return null;
        }
    }
}
