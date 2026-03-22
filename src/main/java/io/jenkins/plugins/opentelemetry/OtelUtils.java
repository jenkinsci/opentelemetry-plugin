/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey.*;

import com.google.common.collect.Iterators;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Plugin;
import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.util.VersionNumber;
import io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import jenkins.scm.api.mixin.TagSCMHead;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

/**
 * Shared utility methods for Jenkins OpenTelemetry instrumentation.
 * <p>
 * This class centralizes helpers for run type detection, debug formatting, version lookup,
 * URL encoding, configuration reporting, and W3C trace-context propagation.
 */
public class OtelUtils {

    /** Job type identifier for classic freestyle projects. */
    public static final String FREESTYLE = "freestyle";
    /** Job type identifier for matrix (multi-configuration) projects. */
    public static final String MATRIX = "matrix";
    /** Job type identifier for Maven projects. */
    public static final String MAVEN = "maven";
    /** Job type identifier for multibranch pipeline projects. */
    public static final String MULTIBRANCH = "multibranch";
    /** Job type identifier for workflow (scripted/declarative pipeline) projects. */
    public static final String WORKFLOW = "workflow";
    /** Fallback job type identifier used when the type cannot be determined. */
    public static final String UNKNOWN = "unknown";
    /** Job type identifier for branch-based builds within multibranch projects. */
    public static final String BRANCH = "branch";
    /** Job type identifier for change-request (pull/merge-request) builds. */
    public static final String CHANGE_REQUEST = "change_request";
    /** Job type identifier for tag-triggered builds. */
    public static final String TAG = "tag";
    /** Attribute value used to represent the Jenkins core component. */
    public static final String JENKINS_CORE = "jenkins-core";
    /** Placeholder value used when an attribute value is unknown. */
    public static final String UNKNOWN_VALUE = "#unknown";

    /**
     * Resolves a configuration value from either Java system properties or environment variables.
     * <p>
     * The environment variable name is converted to a system property name by replacing underscores
     * with dots and lowercasing. System property values take precedence over environment variables.
     *
     * @param environmentVariableName the environment variable name to resolve
     * @return the configured value, or {@code null} if neither source defines a non-blank value
     */
    @CheckForNull
    public static String getSystemPropertyOrEnvironmentVariable(String environmentVariableName) {
        String systemPropertyName = environmentVariableName.replace('_', '.').toLowerCase(Locale.ROOT);
        String systemProperty = System.getProperty(systemPropertyName);
        if (StringUtils.isNotBlank(systemProperty)) {
            return systemProperty;
        }
        String environmentVariable = System.getenv(environmentVariableName);
        if (StringUtils.isNotBlank(environmentVariable)) {
            return environmentVariable;
        }
        return null;
    }

    /**
     * Returns a formatter that converts spans to compact debug strings.
     *
     * @return a formatter function for span debug logging
     */
    @NonNull
    public static Function<Span, String> spanToDebugString() {
        return span -> {
            if (span == null) {
                return "#null#";
            } else if (span instanceof ReadableSpan readableSpan) {
                SpanData spanData = readableSpan.toSpanData();
                return "span(" + "name: "
                        + readableSpan.getName() + ", " + "spanId: "
                        + spanData.getSpanId() + ", " + "parentSpanId: "
                        + spanData.getParentSpanId() + ", " + "traceId: "
                        + spanData.getTraceId() + ", " + ")";
            } else {
                return span.toString();
            }
        };
    }

    /**
     * Determines the Jenkins project type associated with a run.
     *
     * @param run the Jenkins run to inspect
     * @return one of the predefined project type constants
     */
    @NonNull
    public static String getProjectType(Run<?, ?> run) {
        if (isFreestyle(run)) {
            return FREESTYLE;
        }
        if (isMaven(run)) {
            return MAVEN;
        }
        if (isMatrix(run)) {
            return MATRIX;
        }
        if (isMultibranch(run)) {
            return MULTIBRANCH;
        }
        if (isWorkflow(run)) {
            return WORKFLOW;
        }
        return UNKNOWN;
    }

    /**
     * Determines the multibranch subtype associated with a run.
     *
     * @param run the Jenkins run to inspect
     * @return {@code change_request}, {@code branch}, {@code tag}, or {@code unknown}
     */
    @NonNull
    public static String getMultibranchType(Run<?, ?> run) {
        if (isMultibranch(run)) {
            if (isMultibranchChangeRequest(run)) {
                return CHANGE_REQUEST;
            }
            if (isMultibranchBranch(run)) {
                return BRANCH;
            }
            if (isMultibranchTag(run)) {
                return TAG;
            }
        }
        return UNKNOWN;
    }

    /**
     * Returns whether the run belongs to a multibranch tag build.
     *
     * @param run the Jenkins run to inspect
     * @return {@code true} if the run is a multibranch tag build
     */
    public static boolean isMultibranchTag(Run<?, ?> run) {
        if (isMultibranch(run)) {
            return (SCMHead.HeadByItem.findHead(run.getParent()) instanceof TagSCMHead);
        }
        return false;
    }

    /**
     * Returns whether the run belongs to a multibranch change request build.
     *
     * @param run the Jenkins run to inspect
     * @return {@code true} if the run is a change request build
     */
    public static boolean isMultibranchChangeRequest(Run<?, ?> run) {
        if (isMultibranch(run)) {
            return (SCMHead.HeadByItem.findHead(run.getParent()) instanceof ChangeRequestSCMHead);
        }
        return false;
    }

    /**
     * Returns whether the run belongs to a regular multibranch branch build.
     *
     * @param run the Jenkins run to inspect
     * @return {@code true} if the run is a non-tag, non-change-request branch build
     */
    public static boolean isMultibranchBranch(Run<?, ?> run) {
        if (isMultibranch(run)) {
            return !(isMultibranchChangeRequest(run) || isMultibranchTag(run));
        }
        return false;
    }

    /**
     * Returns whether the run belongs to a workflow multibranch project.
     *
     * @param run the Jenkins run to inspect
     * @return {@code true} if the run is from a workflow multibranch project
     */
    public static boolean isMultibranch(Run<?, ?> run) {
        if (run == null) {
            return false;
        }
        return (run instanceof WorkflowRun && run.getParent().getParent() instanceof WorkflowMultiBranchProject);
    }

    /**
     * Returns whether the run belongs to a non-multibranch workflow job.
     *
     * @param run the Jenkins run to inspect
     * @return {@code true} if the run is a standalone workflow run
     */
    public static boolean isWorkflow(Run<?, ?> run) {
        if (run == null) {
            return false;
        }
        return (run instanceof WorkflowRun && !(run.getParent().getParent() instanceof WorkflowMultiBranchProject));
    }

    /**
     * Returns whether the run belongs to a freestyle build.
     *
     * @param run the Jenkins run to inspect
     * @return {@code true} if the run is a freestyle build
     */
    public static boolean isFreestyle(Run<?, ?> run) {
        if (run == null) {
            return false;
        }
        return (run instanceof FreeStyleBuild);
    }

    /**
     * Returns whether the run belongs to a matrix build family.
     *
     * @param run the Jenkins run to inspect
     * @return {@code true} if the run matches known matrix classes
     */
    public static boolean isMatrix(Run<?, ?> run) {
        if (run == null) {
            return false;
        }
        return isInstance(run, "hudson.matrix.MatrixBuild")
                || isInstance(run, "hudson.matrix.MatrixProject")
                || isInstance(run, "hudson.matrix.MatrixRun");
    }

    /**
     * Returns whether the run belongs to a Maven build family.
     *
     * @param run the Jenkins run to inspect
     * @return {@code true} if the run matches known Maven build classes
     */
    public static boolean isMaven(Run<?, ?> run) {
        if (run == null) {
            return false;
        }
        return isInstance(run, "hudson.maven.AbstractMavenBuild")
                || isInstance(run, "hudson.maven.MavenModuleSetBuild")
                || isInstance(run, "hudson.maven.MavenBuild");
    }

    private static boolean isInstance(Object o, String clazz) {
        return o != null && o.getClass().getName().equals(clazz);
    }

    /**
     * Formats a span into a compact debug string.
     *
     * @param span the span to format
     * @return a debug representation of the span
     */
    @NonNull
    public static String toDebugString(@Nullable Span span) {
        return spanToDebugString().apply(span);
    }

    /**
     * Formats a flow node into a compact debug string.
     *
     * @param flowNode the flow node to format
     * @return a debug representation of the flow node
     */
    @NonNull
    public static String toDebugString(FlowNode flowNode) {
        return flowNodeToDebugString().apply(flowNode);
    }

    /**
     * Returns a formatter that converts flow nodes to debug strings.
     *
     * @return a formatter function for flow-node debug logging
     */
    @NonNull
    public static Function<FlowNode, String> flowNodeToDebugString() {
        return flowNode -> flowNode == null
                ? "#null#"
                : "FlowNode[name: " + flowNode.getDisplayName() + ", function: " + flowNode.getDisplayFunctionName()
                        + ", id: " + flowNode.getId() + "]";
    }

    /**
     * URL-encodes a value using UTF-8.
     *
     * @param value the value to encode
     * @return the encoded value
     * @throws IllegalStateException if encoding fails unexpectedly
     */
    @NonNull
    public static String urlEncode(String value) {
        try {
            URLCodec encoder = new URLCodec(StandardCharsets.UTF_8.name());
            return encoder.encode(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to URL encode value: " + value, e);
        }
    }

    /**
     * Returns the running Jenkins core version.
     *
     * @return the Jenkins version string, or {@code #unknown} when unavailable
     */
    @NonNull
    public static String getJenkinsVersion() {
        final VersionNumber versionNumber = Jenkins.getVersion();
        return versionNumber == null
                ? UNKNOWN_VALUE
                : versionNumber.toString(); // should not be null except maybe in development of Jenkins itself
    }

    /**
     * Returns the installed OpenTelemetry plugin version.
     *
     * @return the plugin version, or {@code #unknown} when the plugin instance is unavailable
     */
    @NonNull
    public static String getOpentelemetryPluginVersion() {
        final Jenkins instance = Jenkins.getInstanceOrNull();
        if (instance == null) {
            return UNKNOWN_VALUE;
        }
        final Plugin opentelemetryPlugin = instance.getPlugin("opentelemetry");
        return opentelemetryPlugin == null
                ? UNKNOWN_VALUE
                : opentelemetryPlugin.getWrapper().getVersion();
    }

    private static final List<ConfigurationKey> noteworthyConfigurationPropertyNames = Arrays.asList(
            OTEL_RESOURCE_ATTRIBUTES,
            OTEL_SERVICE_NAME,
            OTEL_TRACES_EXPORTER,
            OTEL_METRICS_EXPORTER,
            OTEL_LOGS_EXPORTER,
            OTEL_EXPORTER_OTLP_ENDPOINT,
            OTEL_EXPORTER_OTLP_TRACES_ENDPOINT,
            OTEL_EXPORTER_OTLP_METRICS_ENDPOINT,
            OTEL_EXPORTER_JAEGER_ENDPOINT,
            OTEL_EXPORTER_PROMETHEUS_PORT,
            OTEL_INSTRUMENTATION_JENKINS_WEB_ENABLED);

    /**
     * Extracts a sorted map of noteworthy OpenTelemetry configuration properties.
     *
     * @param configProperties OpenTelemetry configuration properties
     * @return a map containing configured noteworthy properties
     */
    public static Map<String, String> noteworthyConfigProperties(ConfigProperties configProperties) {
        Map<String, String> noteworthyConfigProperties = new TreeMap<>();
        noteworthyConfigurationPropertyNames.forEach(k -> {
            if (configProperties.getString(k.asProperty()) != null) {
                noteworthyConfigProperties.put(k.asProperty(), configProperties.getString(k.asProperty()));
            }
        });
        return noteworthyConfigProperties;
    }

    /**
     * Creates a W3C trace-context carrier map from the given span.
     *
     * @param span the span whose context should be injected
     * @return a map containing W3C trace-context headers
     */
    public static Map<String, String> getW3cTraceContext(Span span) {
        Map<String, String> w3cTraceContext = new HashMap<>(2);
        try (Scope ignored = span.makeCurrent()) {
            W3CTraceContextPropagator.getInstance()
                    .inject(Context.current(), w3cTraceContext, (carrier, key, value) -> {
                        assert carrier != null;
                        carrier.put(key, value);
                    });
        }
        return w3cTraceContext;
    }

    /**
     * HTTP header getter used by OpenTelemetry propagators when extracting context from servlet requests.
     */
    public static class HttpServletRequestTextMapGetter implements TextMapGetter<HttpServletRequest> {
        /**
         * Returns all header names available on the servlet request.
         *
         * @param request the servlet request
         * @return iterable of header names
         */
        @Override
        public Iterable<String> keys(@NonNull HttpServletRequest request) {
            return () -> Optional.of(request)
                    .map(HttpServletRequest::getHeaderNames)
                    .map((Function<Enumeration<String>, Iterator<String>>) Iterators::forEnumeration)
                    .orElseGet(Collections::emptyIterator);
        }

        /**
         * Returns the header value for a given key.
         *
         * @param request the servlet request
         * @param key the header name
         * @return the header value, or {@code null} if not present
         */
        @Override
        public String get(@javax.annotation.Nullable HttpServletRequest request, @NonNull String key) {
            return Optional.ofNullable(request).map(c -> c.getHeader(key)).orElse(null);
        }
    }
}
