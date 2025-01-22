/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.OpenTelemetryAttributesAction;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class MonitoringComputerListener extends ComputerListener implements OpenTelemetryLifecycleListener {
    private final static Logger LOGGER = Logger.getLogger(MonitoringComputerListener.class.getName());

    private LongCounter failureAgentCounter;

    @Inject
    protected JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    @PostConstruct
    public void postConstruct() {
        Meter meter = jenkinsControllerOpenTelemetry.getDefaultMeter();

        final Jenkins jenkins = Jenkins.get();
        Computer controllerComputer = jenkins.getComputer("");
        if (controllerComputer == null) {
            LOGGER.log(Level.FINE, () -> "IllegalState Jenkins Controller computer not found");
        } else if (controllerComputer.getAction(OpenTelemetryAttributesAction.class) != null) {
            // nothing to do.
            // why are we invoked a second time? plugin reload?
            LOGGER.log(Level.FINER, () -> "Resources for Jenkins Controller computer " + controllerComputer + " have already been defined: " + controllerComputer.getAction(OpenTelemetryAttributesAction.class));
        } else {
            try {
                OpenTelemetryAttributesAction openTelemetryAttributesAction = new OpenTelemetryAttributesAction();
                Map<String, String> attributesAsMap = new GetComputerAttributes().call();
                for (Map.Entry<String, String> attribute : attributesAsMap.entrySet()) {
                    openTelemetryAttributesAction.getAttributes().put(AttributeKey.stringKey(attribute.getKey()), attribute.getValue());
                }
                openTelemetryAttributesAction.getAttributes().put(AttributeKey.stringKey(JenkinsOtelSemanticAttributes.JENKINS_COMPUTER_NAME.getKey()), JenkinsOtelSemanticAttributes.JENKINS_COMPUTER_NAME_CONTROLLER);
                LOGGER.log(Level.FINER, () -> "Resources for Jenkins Controller computer " + controllerComputer + ": " + openTelemetryAttributesAction);
                controllerComputer.addAction(openTelemetryAttributesAction);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failure getting attributes for Jenkins Controller computer " + controllerComputer, e);
            }
        }
        meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_AGENTS_OFFLINE)
            .ofLongs()
            .setDescription("Number of offline agents")
            .setUnit("{agents}")
            .buildWithCallback(valueObserver -> valueObserver.record(this.getOfflineAgentsCount()));
        meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_AGENTS_ONLINE)
            .ofLongs()
            .setDescription("Number of online agents")
            .setUnit("{agents}")
            .buildWithCallback(valueObserver -> valueObserver.record(this.getOnlineAgentsCount()));
        meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_AGENTS_TOTAL)
            .ofLongs()
            .setDescription("Number of agents")
            .setUnit("{agents}")
            .buildWithCallback(valueObserver -> valueObserver.record(this.getAgentsCount()));
        failureAgentCounter = meter.counterBuilder(JenkinsSemanticMetrics.JENKINS_AGENTS_LAUNCH_FAILURE)
            .setDescription("Number of ComputerLauncher failures")
            .setUnit("{agents}")
            .build();

        LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins agents management...");
    }

    private long getOfflineAgentsCount() {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return 0;
        }
        return Arrays.stream(jenkins.getComputers()).filter(Computer::isOffline).count();
    }

    private long getOnlineAgentsCount() {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return 0;
        }
        return Arrays.stream(jenkins.getComputers()).filter(Computer::isOnline).count();
    }

    private long getAgentsCount() {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return 0;
        }
        return Arrays.stream(jenkins.getComputers()).count();
    }

    @Override
    public void preOnline(Computer computer, Channel channel, FilePath root, TaskListener listener) throws IOException, InterruptedException {
        OpenTelemetryAttributesAction openTelemetryAttributesAction = new OpenTelemetryAttributesAction();

        Map<String, String> attributes = channel.call(new GetComputerAttributes());
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            openTelemetryAttributesAction.getAttributes().put(AttributeKey.stringKey(attribute.getKey()), attribute.getValue());
        }
        openTelemetryAttributesAction.getAttributes().put(AttributeKey.stringKey(JenkinsOtelSemanticAttributes.JENKINS_COMPUTER_NAME.getKey()), computer.getName());

        LOGGER.log(Level.FINE, () -> "preOnline(" + computer + "): " + openTelemetryAttributesAction);
        computer.addAction(openTelemetryAttributesAction);
    }

    @Override
    public void onLaunchFailure(Computer computer, TaskListener taskListener) {
        failureAgentCounter.add(1);
        LOGGER.log(Level.FINE, () -> "onLaunchFailure(" + computer + "): ");
    }

    private static class GetComputerAttributes extends MasterToSlaveCallable<Map<String, String>, IOException> {
        @Override
        public Map<String, String> call() throws IOException {
            Map<String, String> attributes = new HashMap<>();
            try {
                InetAddress localHost = InetAddress.getLocalHost();
                if (localHost.isLoopbackAddress()) {
                    // we have a problem, we want another network interface
                }
                attributes.put(HostIncubatingAttributes.HOST_NAME.getKey(), localHost.getHostName());
                attributes.put(HostIncubatingAttributes.HOST_IP.getKey(), localHost.getHostAddress());
            } catch (IOException e) {
                // as this code will go through Jenkins remoting, test isLoggable before transferring data
                if (LOGGER.isLoggable(Level.FINER)) {
                    MonitoringComputerListener.LOGGER.log(Level.FINER, "Exception retrieving the build agent host details", e);
                } else if (LOGGER.isLoggable(Level.FINE)) {
                    MonitoringComputerListener.LOGGER.log(Level.FINE, () -> "Exception retrieving the build agent host details " + e.getMessage());
                }
            }
            return attributes;
        }
    }
}
