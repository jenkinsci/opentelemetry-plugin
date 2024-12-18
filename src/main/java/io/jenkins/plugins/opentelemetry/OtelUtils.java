/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

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
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import jenkins.scm.api.mixin.TagSCMHead;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

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
import java.util.logging.Logger;

import static io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey.*;

public class OtelUtils {

    public static final String FREESTYLE = "freestyle";
    public static final String MATRIX = "matrix";
    public static final String MAVEN = "maven";
    public static final String MULTIBRANCH = "multibranch";
    public static final String WORKFLOW = "workflow";
    public static final String UNKNOWN = "unknown";
    public static final String BRANCH = "branch";
    public static final String CHANGE_REQUEST = "change_request";
    public static final String TAG = "tag";
    public static final String JENKINS_CORE = "jenkins-core";
    public static final String UNKNOWN_VALUE = "#unknown";

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

    @NonNull
    public static Function<Span, String> spanToDebugString() {
        return span -> {
            if (span == null) {
                return "#null#";
            } else if (span instanceof ReadableSpan readableSpan) {
                SpanData spanData = readableSpan.toSpanData();
                return "span(" +
                    "name: " + readableSpan.getName() + ", " +
                    "spanId: " + spanData.getSpanId() + ", " +
                    "parentSpanId: " + spanData.getParentSpanId() + ", " +
                    "traceId: " + spanData.getTraceId() + ", " +
                    ")";
            } else {
                return span.toString();
            }
        };
    }

    @NonNull
    public static String getProjectType(Run<?,?> run) {
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

    @NonNull
    public static String getMultibranchType(Run<?,?> run) {
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

    public static boolean isMultibranchTag(Run<?,?> run) {
        if (isMultibranch(run)) {
            return (SCMHead.HeadByItem.findHead(run.getParent()) instanceof TagSCMHead);
        }
        return false;
    }

    public static boolean isMultibranchChangeRequest(Run<?,?> run) {
        if (isMultibranch(run)) {
            return (SCMHead.HeadByItem.findHead(run.getParent()) instanceof ChangeRequestSCMHead);
        }
        return false;
    }

    public static boolean isMultibranchBranch(Run<?,?> run) {
        if (isMultibranch(run)) {
            return !(isMultibranchChangeRequest(run) || isMultibranchTag(run));
        }
        return false;
    }

    public static boolean isMultibranch(Run<?,?> run) {
        if (run == null) {
            return false;
        }
        return (run instanceof WorkflowRun && run.getParent().getParent() instanceof WorkflowMultiBranchProject);
    }

    public static boolean isWorkflow(Run<?,?> run) {
        if (run == null) {
            return false;
        }
        return (run instanceof WorkflowRun && !(run.getParent().getParent() instanceof WorkflowMultiBranchProject));
    }

    public static boolean isFreestyle(Run<?,?> run) {
        if (run == null) {
            return false;
        }
        return (run instanceof FreeStyleBuild);
    }

    public static boolean isMatrix(Run<?,?> run) {
        if (run == null) {
            return false;
        }
        return isInstance(run, "hudson.matrix.MatrixBuild") ||
            isInstance(run, "hudson.matrix.MatrixProject") ||
            isInstance(run, "hudson.matrix.MatrixRun");
    }

    public static boolean isMaven(Run<?,?> run) {
        if (run == null) {
            return false;
        }
        return isInstance(run, "hudson.maven.AbstractMavenBuild") ||
            isInstance(run, "hudson.maven.MavenModuleSetBuild") ||
            isInstance(run, "hudson.maven.MavenBuild");
    }

    private static boolean isInstance(Object o, String clazz) {
        return o != null && o.getClass().getName().equals(clazz);
    }

    @NonNull
    public static String toDebugString(@Nullable Span span) {
        return spanToDebugString().apply(span);
    }

    @NonNull
    public static String toDebugString(FlowNode flowNode) {
        return flowNodeToDebugString().apply(flowNode);
    }

    @NonNull
    public static Function<FlowNode, String> flowNodeToDebugString() {
        return  flowNode -> flowNode == null ? "#null#" : "FlowNode[name: " + flowNode.getDisplayName() + ", function: " + flowNode.getDisplayFunctionName() + ", id: " + flowNode.getId() + "]";
    }

    @NonNull
    public static String urlEncode(String value) {
        try {
            URLCodec encoder = new URLCodec(StandardCharsets.UTF_8.name());
            return encoder.encode(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to URL encode value: " + value, e);
        }
    }

    @NonNull
    public static String getJenkinsVersion() {
        final VersionNumber versionNumber = Jenkins.getVersion();
        return versionNumber == null ? UNKNOWN_VALUE : versionNumber.toString(); // should not be null except maybe in development of Jenkins itself
    }

    @NonNull
    public static String getOpentelemetryPluginVersion() {
        final Jenkins instance = Jenkins.getInstanceOrNull();
        if (instance == null) {
            return UNKNOWN_VALUE;
        }
        final Plugin opentelemetryPlugin = instance.getPlugin("opentelemetry");
        return opentelemetryPlugin == null ? UNKNOWN_VALUE : opentelemetryPlugin.getWrapper().getVersion();
    }

    private final static List<ConfigurationKey> noteworthyConfigurationPropertyNames = Arrays.asList(
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

    public static Map<String, String> noteworthyConfigProperties(ConfigProperties configProperties) {
        Map<String, String> noteworthyConfigProperties = new TreeMap<>();
        noteworthyConfigurationPropertyNames.forEach(k -> {
            if (configProperties.getString(k.asProperty()) != null) {
                noteworthyConfigProperties.put(k.asProperty(), configProperties.getString(k.asProperty()));
            }
        });
        return noteworthyConfigProperties;
    }

    public static Map<String, String> getW3cTraceContext(Span span) {
        Map<String, String> w3cTraceContext = new HashMap<>(2);
        try (Scope ignored = span.makeCurrent()) {
            W3CTraceContextPropagator.getInstance().inject(Context.current(), w3cTraceContext, (carrier, key, value) -> {
                assert carrier != null;
                carrier.put(key, value);
            });
        }
        return w3cTraceContext;
    }

    public static class HttpServletRequestTextMapGetter implements TextMapGetter<HttpServletRequest> {
        @Override
        public Iterable<String> keys(@NonNull HttpServletRequest request) {
            return () -> Optional.of(request)
                .map(HttpServletRequest::getHeaderNames)
                .map((Function<Enumeration<String>, Iterator<String>>) Iterators::forEnumeration)
                .orElseGet(Collections::emptyIterator);
        }

        @Override
        public String get(@javax.annotation.Nullable HttpServletRequest request, @NonNull String key) {
            return Optional.ofNullable(request)
                .map(c -> c.getHeader(key))
                .orElse(null);
        }
    }
}
