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
import io.jenkins.plugins.opentelemetry.api.semconv.JenkinsAttributes;
import io.jenkins.plugins.opentelemetry.opentelemetry.GlobalOpenTelemetrySdk;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>Instantiate and configure OpenTelemetry SDKs on the Jenkins build agents</p>
 * <p>support TODO support disabling OTel SDKs on configuration change, after it has been enabled</p>
 */
@Extension(ordinal = Integer.MAX_VALUE)
public class OpenTelemetryConfigurerComputerListener extends ComputerListener implements OpenTelemetryLifecycleListener {

    private static final Logger logger = Logger.getLogger(OpenTelemetryConfigurerComputerListener.class.getName());

    final AtomicBoolean buildAgentsInstrumentationEnabled = new AtomicBoolean(false);

    JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration;

    @Override
    public void preOnline(Computer computer, Channel channel, FilePath root, TaskListener listener) {
        if (!buildAgentsInstrumentationEnabled.get()) {
            return;
        }
        OpenTelemetryConfiguration openTelemetryConfiguration = jenkinsOpenTelemetryPluginConfiguration.toOpenTelemetryConfiguration();
        Map<String, String> otelSdkProperties = openTelemetryConfiguration.toOpenTelemetryProperties();
        Map<String, String> otelSdkResourceProperties = openTelemetryConfiguration.toOpenTelemetryResourceAsMap();

        try {
            Object result = configureOpenTelemetrySdkOnComputer(computer, channel, otelSdkProperties, otelSdkResourceProperties).get();
            logger.log(Level.FINE, () -> "Updated OpenTelemetry configuration for computer/build-agent '" + computer.getName() + "' with result: " + result);
        } catch (InterruptedException e) {
            logger.log(Level.INFO, e, () -> "Failure to update OpenTelemetry configuration for computer/build-agent '" + computer.getName() + "'");
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.log(Level.INFO, e, () -> "Failure to update OpenTelemetry configuration for computer/build-agent '" + computer.getName() + "'");
        }
    }

    @Inject
    public void setJenkinsOpenTelemetryPluginConfiguration(JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration) {
        this.jenkinsOpenTelemetryPluginConfiguration = jenkinsOpenTelemetryPluginConfiguration;
    }

    /**
     * <p>
     * Propagate config change to all the build agents.
     * </p>
     * <p>
     * TODO only update build agent configuration if it has changed
     * </p>
     */
    @Override
    public void afterConfiguration(ConfigProperties configProperties) {

        // Update the configuration of the Jenkins build agents
        OpenTelemetryConfiguration openTelemetryConfiguration = jenkinsOpenTelemetryPluginConfiguration.toOpenTelemetryConfiguration();

        boolean otlpLogsEnabled = "otlp".equals(configProperties.getString("otel.logs.exporter")); // pipeline logs export to OTLP endpoint activated
        boolean jenkinsAgentInstrumentationDisabled = "false".equalsIgnoreCase(configProperties.getString(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_AGENTS_ENABLED));
        this.buildAgentsInstrumentationEnabled.set(otlpLogsEnabled || !jenkinsAgentInstrumentationDisabled);
        if (!buildAgentsInstrumentationEnabled.get()) {
            return;
        }

        Map<String, String> otelSdkProperties = openTelemetryConfiguration.toOpenTelemetryProperties();
        Map<String, String> otelSdkResourceProperties = openTelemetryConfiguration.toOpenTelemetryResourceAsMap();

        Computer[] computers = Jenkins.get().getComputers();
        List<Future<Object>> configureAgentResults = new ArrayList<>(computers.length);
        Arrays.stream(computers).forEach(computer -> {
            Node node = computer.getNode();
            VirtualChannel channel = computer.getChannel();

            logger.log(Level.FINE, () ->
                "Evaluate computer.name: '" + computer.getName() +
                    "', node: " + Optional.ofNullable(node).map(n -> n.getNodeName() + " / " + n.getClass().getName()));

            if (node instanceof Jenkins) {
                // skip Jenkins controller
            } else if (channel == null) {
                logger.log(Level.FINE, () -> "Failure to update OpenTelemetry configuration for computer/build-agent '" + computer.getName() + "' as its channel is null, probably offline");
            } else {
                configureAgentResults.add(configureOpenTelemetrySdkOnComputer(computer, channel, otelSdkProperties, otelSdkResourceProperties));
            }
        });
        configureAgentResults.forEach(result -> {
                try {
                    result.get(10, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    logger.log(Level.WARNING, e, () -> "Failure to update OpenTelemetry configuration for computer/build-agent");
                }
            }
        );
    }

    /**
     * @param channel pass the channel rather than using {@link Computer#getChannel()} to support {@link ComputerListener#preOnline(Computer, Channel, FilePath, TaskListener)} use cases
     */
    private Future<Object> configureOpenTelemetrySdkOnComputer(@Nonnull Computer computer, @Nonnull VirtualChannel channel, Map<String, String> otelSdkProperties, Map<String, String> otelSdkResourceProperties) {
        Map<String, String> buildAgentOtelSdkProperties;
        Map<String, String> buildAgentOtelSdkResourceProperties;
        final Set<String> filteredResourceKeys = Set.of(
            ServiceAttributes.SERVICE_NAME.getKey(),
            ServiceIncubatingAttributes.SERVICE_INSTANCE_ID.getKey()
        );
        buildAgentOtelSdkProperties = new HashMap<>(otelSdkProperties);
        buildAgentOtelSdkResourceProperties = new HashMap<>();
        otelSdkResourceProperties
            .entrySet()
            .stream()
            .filter(Predicate.not(entry -> filteredResourceKeys.contains(entry.getKey())))
            .forEach(entry -> buildAgentOtelSdkResourceProperties.put(entry.getKey(), entry.getValue()));
        // use the same service.name for the Jenkins build agent in order to not break visualization
        // of pipeline logs stored externally (Loki, Elasticsearch...) as these visualization logics
        // may query on the service name
        String serviceName = Optional.ofNullable(otelSdkResourceProperties.get(ServiceAttributes.SERVICE_NAME.getKey())).orElse(JenkinsAttributes.JENKINS);// + "-agent";
        buildAgentOtelSdkResourceProperties.put(ServiceAttributes.SERVICE_NAME.getKey(), serviceName);
        buildAgentOtelSdkResourceProperties.put(JenkinsOtelSemanticAttributes.JENKINS_COMPUTER_NAME.getKey(), computer.getName());
        buildAgentOtelSdkResourceProperties.put(JenkinsOtelSemanticAttributes.JENKINS_COMPUTER_NAME.getKey(), computer.getName());

        OpenTelemetryConfigurerMasterToSlaveCallable callable;
        callable = new OpenTelemetryConfigurerMasterToSlaveCallable(buildAgentOtelSdkProperties, buildAgentOtelSdkResourceProperties);

        try {
            logger.log(Level.FINE, () -> "Updating OpenTelemetry configuration for computer/build-agent '" + computer.getName() + "'...");
            return channel.callAsync(callable);
        } catch (IOException | RuntimeException e) {
            logger.log(Level.INFO, e, () -> "Failure to update OpenTelemetry configuration for computer/build-agent '" + computer.getName() + "'");
            return CompletableFuture.completedFuture(e);
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
            logger.log(Level.FINE, () -> "Configure OpenTelemetry SDK with properties: " + otelSdkConfigurationProperties + ", resource:" + otelSdkResource);
            GlobalOpenTelemetrySdk.configure(otelSdkConfigurationProperties, otelSdkResource, true);
            return null;
        }
    }
}
