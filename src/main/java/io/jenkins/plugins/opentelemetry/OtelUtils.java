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
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.ResourceAttributes;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import jenkins.scm.api.mixin.TagSCMHead;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            } else if (span instanceof ReadableSpan) {
                ReadableSpan readableSpan = (ReadableSpan) span;
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
    public static String getProjectType(Run run) {
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
    public static String getMultibranchType(Run run) {
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

    public static boolean isMultibranchTag(Run run) {
        if (isMultibranch(run)) {
            return (SCMHead.HeadByItem.findHead(run.getParent()) instanceof TagSCMHead);
        }
        return false;
    }

    public static boolean isMultibranchChangeRequest(Run run) {
        if (isMultibranch(run)) {
            return (SCMHead.HeadByItem.findHead(run.getParent()) instanceof ChangeRequestSCMHead);
        }
        return false;
    }

    public static boolean isMultibranchBranch(Run run) {
        if (isMultibranch(run)) {
            return !(isMultibranchChangeRequest(run) || isMultibranchTag(run));
        }
        return false;
    }

    public static boolean isMultibranch(Run run) {
        if (run == null) {
            return false;
        }
        return (run instanceof WorkflowRun && run.getParent().getParent() instanceof WorkflowMultiBranchProject);
    }

    public static boolean isWorkflow(Run run) {
        if (run == null) {
            return false;
        }
        return (run instanceof WorkflowRun && !(run.getParent().getParent() instanceof WorkflowMultiBranchProject));
    }

    public static boolean isFreestyle(Run run) {
        if (run == null) {
            return false;
        }
        return (run instanceof FreeStyleBuild);
    }

    @NonNull
    public static boolean isMatrix(Run run) {
        if (run == null) {
            return false;
        }
        return isInstance(run, "hudson.matrix.MatrixBuild") ||
            isInstance(run, "hudson.matrix.MatrixProject") ||
            isInstance(run, "hudson.matrix.MatrixRun");
    }

    public static boolean isMaven(Run run) {
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

    @Nonnull
    public static String toDebugString(FlowNode flowNode) {
        return flowNodeToDebugString().apply(flowNode);
    }

    @NonNull
    public static Function<FlowNode, String> flowNodeToDebugString() {
        return  flowNode -> flowNode == null ? "#null#" : "FlowNode[name: " + flowNode.getDisplayName() + ", function: " + flowNode.getDisplayFunctionName() + ", id: " + flowNode.getId() + "]";
    }

    @NonNull
    public static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

    public static String prettyPrintOtelSdkConfig(ConfigProperties configProperties, Resource resource) {
        return "SDK [" +
            "config: " + prettyPrintConfiguration(configProperties) + ", "+
            "resource: " + prettyPrintResource(resource) +
            "]";
    }
    private final static List<String> noteworthyConfigurationPropertyNames = Arrays.asList(
        "otel.resource.attributes", "otel.service.name",
        "otel.traces.exporter", "otel.metrics.exporter", "otel.logs.exporter",
        "otel.exporter.otlp.endpoint"  , "otel.exporter.otlp.traces.endpoint", "otel.exporter.otlp.metrics.endpoint",
        "otel.exporter.jaeger.endpoint", "otel.exporter.prometheus.port",
        JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_WEB_ENABLED);

    private final static List<AttributeKey> noteworthyResourceAttributeKeys = Arrays.asList(
        ResourceAttributes.SERVICE_NAME, ResourceAttributes.SERVICE_NAMESPACE, ResourceAttributes.SERVICE_VERSION
    ) ;

    public static String prettyPrintConfiguration(ConfigProperties config) {
        Map<String, String> message = new LinkedHashMap<>();
        for (String attributeName : noteworthyConfigurationPropertyNames) {
            final String attributeValue = config.getString(attributeName);
            if (attributeValue != null) {
                message.put(attributeName, attributeValue);
            }
        }
        return message.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(", "));
    }

    public static Map<String, String> noteworthyConfigProperties(ConfigProperties configProperties) {
        Map<String, String> noteworthyConfigProperties = new TreeMap<>();
        noteworthyConfigurationPropertyNames.forEach(k -> {
            if (configProperties.getString(k) != null) {
                noteworthyConfigProperties.put(k, configProperties.getString(k));
            }
        });
        return noteworthyConfigProperties;
    }

    public static Map<String, String> getW3cTraceContext(Span span) {
        Map<String, String> w3cTraceContext = new HashMap<>(2);
        try (Scope scope = span.makeCurrent()) {
            W3CTraceContextPropagator.getInstance().inject(Context.current(), w3cTraceContext, (carrier, key, value) -> {
                assert carrier != null;
                carrier.put(key, value);
            });
        }
        return w3cTraceContext;
    }
    public static String prettyPrintResource(@Nullable Resource resource) {
        if (resource == null) {
            return "#null#";
        }
        Map<String, String> message = new LinkedHashMap<>();
        for (AttributeKey attributeKey : noteworthyResourceAttributeKeys) {
            Object attributeValue = resource.getAttribute(attributeKey);
            if (attributeValue != null) {
                message.put(attributeKey.getKey(), Objects.toString(attributeValue, "#null#"));
            }
        }
        return message.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(", "));
    }

    public static class HttpServletRequestTextMapGetter implements TextMapGetter<HttpServletRequest> {
        @Override
        public Iterable<String> keys(@Nonnull HttpServletRequest request) {
            return () -> Optional.of(request)
                .map(HttpServletRequest::getHeaderNames)
                .map((Function<Enumeration<String>, Iterator<String>>) Iterators::forEnumeration)
                .orElseGet(Collections::emptyIterator);
        }

        @Override
        public String get(@javax.annotation.Nullable HttpServletRequest request, @Nonnull String key) {
            return Optional.ofNullable(request)
                .map(c -> c.getHeader(key))
                .orElse(null);
        }
    }
}
