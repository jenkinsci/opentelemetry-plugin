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
import io.opentelemetry.api.trace.Span;
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
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * Span reference associate with a {@link Run}
 */
public class MonitoringAction extends AbstractMonitoringAction
        implements Action, RunAction2, SimpleBuildStep.LastBuildAction {

    private static final Logger LOGGER = Logger.getLogger(MonitoringAction.class.getName());

    // Backward compatibility
    @Deprecated(since = "2.15.1", forRemoval = true)
    private String rootSpanName;

    @Deprecated(since = "2.15.1", forRemoval = true)
    private Map<String, String> rootContext;

    private transient Run run;

    /**
     * Creates a monitoring action backed by the given span.
     *
     * @param span root span for the build
     */
    public MonitoringAction(Span span) {
        super(span, Collections.emptyList());
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

    /**
     * Returns icon file name for the action sidebar link.
     *
     * @return {@code null} — this action has no sidebar icon
     */
    @Override
    public String getIconFileName() {
        return null;
    }

    /**
     * Returns display name label for the action.
     *
     * @return display name
     */
    @Override
    public String getDisplayName() {
        return "OpenTelemetry";
    }

    /**
     * Returns project-level actions from the last successful build.
     *
     * @return project-level monitoring actions
     */
    @Override
    public Collection<? extends Action> getProjectActions() {
        return run.getParent().getLastSuccessfulBuild().getActions(MonitoringAction.class);
    }

    /**
     * Returns URL path segment for the action.
     *
     * @return {@code null} — this action has no dedicated URL
     */
    @Override
    public String getUrlName() {
        return null;
    }

    /**
     * Backward compatibility
     */
    protected Object readResolve() {
        if (this.rootContext != null && this.w3cTraceContext == null) {
            LOGGER.log(
                    Level.FINEST,
                    () -> "Migrate rootContext='" + this.rootContext + "' on " + System.identityHashCode(this));
            this.w3cTraceContext = this.rootContext;
            this.rootContext = null;
        }
        if (this.rootSpanName != null && this.spanName == null) {
            LOGGER.log(
                    Level.FINEST,
                    () -> "Migrate rootSpanName='" + this.rootSpanName + "' on " + System.identityHashCode(this));
            this.spanName = this.rootSpanName;
            this.rootSpanName = null;
        }
        return this;
    }

    /**
     * Returns W3C trace context for a specific pipeline flow node.
     *
     * @param flowNodeId pipeline flow node identifier
     * @return W3C trace context map, or {@code null} when unavailable
     */
    @CheckForNull
    public Map<String, String> getW3cTraceContext(@NonNull String flowNodeId) {
        Optional<FlowNode> flowNode = Optional.ofNullable(((WorkflowRun) run).getExecution())
                .map(flowExecution -> {
                    try {
                        return flowExecution.getNode(flowNodeId);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failure to retrieve flow node " + flowNodeId, e);
                        return null;
                    }
                });

        return flowNode.flatMap(
                        node -> ImmutableList.copyOf(node.getActions(FlowNodeMonitoringAction.class)).reverse().stream()
                                .findFirst()
                                .map(FlowNodeMonitoringAction::getW3cTraceContext))
                .orElseGet(() -> run.getActions(MonitoringAction.class).stream()
                        .findFirst()
                        .map(MonitoringAction::getW3cTraceContext)
                        .orElse(Collections.emptyMap()));
    }

    /**
     * Returns observability backend links generated from configured backends for the current run.
     *
     * @return list of backend trace visualization links
     */
    @NonNull
    public List<ObservabilityBackendLink> getLinks() {
        List<ObservabilityBackend> tracingCapableBackends =
                JenkinsOpenTelemetryPluginConfiguration.get().getObservabilityBackends().stream()
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
        binding.put(
                ObservabilityBackend.TemplateBindings.SERVICE_NAME,
                Objects.requireNonNull(
                        JenkinsOpenTelemetryPluginConfiguration.get().getServiceName()));
        binding.put(
                ObservabilityBackend.TemplateBindings.SERVICE_NAMESPACE,
                JenkinsOpenTelemetryPluginConfiguration.get().getServiceNamespace());
        binding.put(
                ObservabilityBackend.TemplateBindings.ROOT_SPAN_NAME,
                this.rootSpanName == null ? null : OtelUtils.urlEncode(this.rootSpanName));
        binding.put(ObservabilityBackend.TemplateBindings.TRACE_ID, this.getTraceId());
        binding.put(ObservabilityBackend.TemplateBindings.SPAN_ID, this.getSpanId());
        binding.put(ObservabilityBackend.TemplateBindings.START_TIME, Instant.ofEpochMilli(run.getStartTimeInMillis()));

        return tracingCapableBackends.stream()
                .map(backend -> new ObservabilityBackendLink(
                        "View pipeline with " + backend.getName(),
                        backend.getTraceVisualisationUrl(binding),
                        backend.getIconPath(),
                        backend.getEnvVariableName()))
                .collect(Collectors.toList());
    }

    public static class ObservabilityBackendLink {
        final String label;
        final String url;
        final String iconClass;
        final String environmentVariableName;

        /**
         * Creates an observability backend link.
         *
         * @param label human-readable link label
         * @param url observability backend URL
         * @param iconClass CSS icon class
         * @param environmentVariableName environment variable name for this backend
         */
        public ObservabilityBackendLink(String label, String url, String iconClass, String environmentVariableName) {
            this.label = label;
            this.url = url;
            this.iconClass = iconClass;
            this.environmentVariableName = environmentVariableName;
        }

        /**
         * Returns human-readable link label.
         *
         * @return link label
         */
        public String getLabel() {
            return label;
        }

        /**
         * Returns observability backend URL.
         *
         * @return backend URL
         */
        public String getUrl() {
            return url;
        }

        /**
         * Returns CSS icon class for the link.
         *
         * @return CSS icon class
         */
        public String getIconClass() {
            return iconClass;
        }

        /**
         * Returns environment variable name that holds this backend URL.
         *
         * @return environment variable name
         */
        public String getEnvironmentVariableName() {
            return environmentVariableName;
        }

        @Override
        public String toString() {
            return "ObservabilityBackendLink{" + "label='"
                    + label + '\'' + ", url='"
                    + url + '\'' + ", iconUrl='"
                    + iconClass + '\'' + ", environmentVariableName='"
                    + environmentVariableName + '\'' + '}';
        }
    }
}
