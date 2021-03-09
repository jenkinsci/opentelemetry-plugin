/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;
import hudson.slaves.OfflineCause;
import io.jenkins.plugins.opentelemetry.OpenTelemetryAttributesAction;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.LongValueRecorder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class MonitoringComputerListener extends ComputerListener {
    private final static Logger LOGGER = Logger.getLogger(MonitoringComputerListener.class.getName());

    protected Meter meter;
    private LongValueRecorder agentsRecorder;
    private LongValueRecorder onlineAgentsRecorder;
    private LongValueRecorder offlineAgentsRecorder;

    @PostConstruct
    public void postConstruct() {
        Computer controllerComputer = Jenkins.get().getComputer("");
        if (controllerComputer == null) {
            LOGGER.log(Level.FINE, () -> "IllegalState Jenkins Controller computer not found");
        } else if (controllerComputer.getAction(OpenTelemetryAttributesAction.class) != null) {
            // nothing to do.
            // why are we invoked a second time? plugin reload?
            LOGGER.log(Level.FINE, () -> "Resources for Jenkins Controller computer " + controllerComputer + " have already been defined: " + controllerComputer.getAction(OpenTelemetryAttributesAction.class));
        } else {
            try {
                OpenTelemetryAttributesAction openTelemetryAttributesAction = new OpenTelemetryAttributesAction();
                Map<String, String> attributesAsMap = new GetComputerAttributes().call();
                for (Map.Entry<String, String> attribute : attributesAsMap.entrySet()) {
                    openTelemetryAttributesAction.getAttributes().put(AttributeKey.stringKey(attribute.getKey()), attribute.getValue());
                }
                openTelemetryAttributesAction.getAttributes().put(AttributeKey.stringKey(JenkinsOtelSemanticAttributes.JENKINS_COMPUTER_NAME.getKey()), JenkinsOtelSemanticAttributes.JENKINS_COMPUTER_NAME_CONTROLLER);
                LOGGER.log(Level.FINE, () -> "Resources for Jenkins Controller computer " + controllerComputer + ": " + openTelemetryAttributesAction);
                controllerComputer.addAction(openTelemetryAttributesAction);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,  "Failure getting attributes for Jenkins Controller computer " + controllerComputer, e);
            }
        }
        offlineAgentsRecorder = meter.longValueRecorderBuilder(JenkinsSemanticMetrics.JENKINS_AGENTS_OFFLINE)
                .setDescription("Number of offline agents")
                .setUnit("1")
                .build();
        onlineAgentsRecorder = meter.longValueRecorderBuilder(JenkinsSemanticMetrics.JENKINS_AGENTS_ONLINE)
                .setDescription("Number of offline agents")
                .setUnit("1")
                .build();
        agentsRecorder = meter.longValueRecorderBuilder(JenkinsSemanticMetrics.JENKINS_AGENTS_TOTAL)
                .setDescription("Number of agents")
                .setUnit("1")
                .build();
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
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        super.onOnline(c, listener);
        updateAgents();
    }

    @Override
    public void onOffline(@NonNull Computer c, @CheckForNull OfflineCause cause) {
        super.onOffline(c, cause);
        updateAgents();
    }

    private static class GetComputerAttributes extends MasterToSlaveCallable<Map<String, String>, IOException> {
        @Override
        public Map<String, String> call() throws IOException {
            Map<String, String> attributes = new HashMap<>();

            InetAddress localHost = InetAddress.getLocalHost();
            if (localHost.isLoopbackAddress()) {
                // we have a problem, we want another network interface
            }
            attributes.put(ResourceAttributes.HOST_NAME.getKey(), localHost.getHostName());
            attributes.put("host.ip", localHost.getHostAddress());
            return attributes;
        }
    }

    private void updateAgents() {
        Jenkins jenkins = Jenkins.getInstance();
        Computer[] computers = new Computer[0];
        if(jenkins != null){
            computers = jenkins.getComputers();
        }
        long offlineAgents = 0;
        long onlineAgents = 0;
        for (Computer computer : computers) {
            if (computer.isOffline()) {
                offlineAgents++;
            }
            if (computer.isOnline()) {
                onlineAgents++;
            }
        }
        this.offlineAgentsRecorder.record(offlineAgents);
        this.onlineAgentsRecorder.record(onlineAgents);
        this.agentsRecorder.record(computers.length);
    }
}
