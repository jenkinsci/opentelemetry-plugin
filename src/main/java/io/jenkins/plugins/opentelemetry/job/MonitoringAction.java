/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.job.action.AbstractMonitoringAction;
import io.jenkins.plugins.opentelemetry.job.action.FlowNodeMonitoringAction;
import io.jenkins.plugins.opentelemetry.job.action.OtelMonitoringAction;
import io.opentelemetry.api.trace.Span;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Span reference associate with a {@link Run}
 */
public class MonitoringAction extends AbstractMonitoringAction implements Action, RunAction2, SimpleBuildStep.LastBuildAction {

    private final static Logger LOGGER = Logger.getLogger(MonitoringAction.class.getName());

    // Backward compatibility
    @Deprecated(since = "2.15.1", forRemoval = true)
    private String rootSpanName;
    @Deprecated(since = "2.15.1", forRemoval = true)
    private Map<String, String> rootContext;
    private transient Run run;

    public MonitoringAction(Span span) {
        super(span);
        this.rootSpanName = super.getSpanName();
        this.rootContext = super.getW3cTraceContext();
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "OpenTelemetry";
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return run.getParent().getLastSuccessfulBuild().getActions(MonitoringAction.class);
    }

    @Override
    public String getUrlName() {
        return null;
    }

    /**
     * Backward compatibility
     */
    protected Object readResolve() {
        if (this.rootContext != null && this.w3cTraceContext == null) {
            LOGGER.log(Level.FINEST, () -> "Migrate rootContext='" + this.rootContext + "' on " + System.identityHashCode(this));
            this.w3cTraceContext = this.rootContext;
            this.rootContext = null;
        }
        if (this.rootSpanName != null && this.spanName == null) {
            LOGGER.log(Level.FINEST, () -> "Migrate rootSpanName='" + this.rootSpanName + "' on " + System.identityHashCode(this));
            this.spanName = this.rootSpanName;
            this.rootSpanName = null;
        }
        return this;
    }

    @CheckForNull
    public Map<String, String> getW3cTraceContext(@NonNull String flowNodeId) {
        Optional<FlowNode> flowNode = Optional.ofNullable(((WorkflowRun) run).getExecution()).map(flowExecution -> {
            try {
                return flowExecution.getNode(flowNodeId);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failure to retrieve flow node " + flowNodeId, e);
                return null;
            }
        });

        return flowNode.flatMap(node -> ImmutableList
                .copyOf(node.getActions(FlowNodeMonitoringAction.class))
                .reverse()
                .stream()
                .findFirst()
                .map(FlowNodeMonitoringAction::getW3cTraceContext))
            .orElseGet(() ->
                run.getActions(MonitoringAction.class)
                    .stream()
                    .findFirst()
                    .map(MonitoringAction::getW3cTraceContext)
                    .orElse(Collections.emptyMap()));
    }

    @NonNull
    public List<ObservabilityBackendLink> getLinks() {
        List<ObservabilityBackend> tracingCapableBackends = JenkinsOpenTelemetryPluginConfiguration.get().getObservabilityBackends()
            .stream()
            .filter(backend -> backend.getTraceVisualisationUrlTemplate() != null)
            .collect(Collectors.toList());

        if (tracingCapableBackends.isEmpty()) {
            return Collections.singletonList(new ObservabilityBackendLink(
                "Please define an OpenTelemetry Visualisation URL of pipelines in Jenkins configuration",
                Jenkins.get().getRootUrl() + "/configure",
                "icon-gear2",
                null));
        }
        Map<String, Object> binding = new HashMap<>();
        binding.put("serviceName", Objects.requireNonNull(JenkinsOpenTelemetryPluginConfiguration.get().getServiceName()));
        binding.put("rootSpanName", this.rootSpanName == null ? null : OtelUtils.urlEncode(this.rootSpanName));
        binding.put("traceId", this.getTraceId());
        binding.put("spanId", this.getSpanId());
        binding.put("startTime", Instant.ofEpochMilli(run.getStartTimeInMillis()));

        return tracingCapableBackends.stream().map(backend ->
            new ObservabilityBackendLink(
                "View pipeline with " + backend.getName(),
                backend.getTraceVisualisationUrl(binding),
                backend.getIconPath(),
                backend.getEnvVariableName())
        ).collect(Collectors.toList());
    }

    public static class ObservabilityBackendLink {
        final String label;
        final String url;
        final String iconClass;
        final String environmentVariableName;

        public ObservabilityBackendLink(String label, String url, String iconClass, String environmentVariableName) {
            this.label = label;
            this.url = url;
            this.iconClass = iconClass;
            this.environmentVariableName = environmentVariableName;
        }

        public String getLabel() {
            return label;
        }

        public String getUrl() {
            return url;
        }

        public String getIconClass() {
            return iconClass;
        }

        public String getEnvironmentVariableName() {
            return environmentVariableName;
        }

        @Override
        public String toString() {
            return "ObservabilityBackendLink{" +
                "label='" + label + '\'' +
                ", url='" + url + '\'' +
                ", iconUrl='" + iconClass + '\'' +
                ", environmentVariableName='" + environmentVariableName + '\'' +
                '}';
        }
    }
}
