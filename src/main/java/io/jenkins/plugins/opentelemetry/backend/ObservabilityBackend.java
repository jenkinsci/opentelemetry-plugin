/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.MissingPropertyException;
import groovy.text.GStringTemplateEngine;
import groovy.text.Template;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.opentelemetry.sdk.resources.Resource;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundSetter;

public abstract class ObservabilityBackend
        implements Describable<ObservabilityBackend>, ExtensionPoint, TemplateBindingsProvider {
    private static final Logger LOGGER = Logger.getLogger(ObservabilityBackend.class.getName());

    public static final String ICONS_PREFIX = "plugin/opentelemetry/images/svgs/";

    private String name;

    @CheckForNull
    public abstract String getTraceVisualisationUrlTemplate();

    private transient Template traceVisualisationUrlGTemplate;

    @CheckForNull
    public abstract String getMetricsVisualizationUrlTemplate();

    private transient Template metricsVisualizationUrlGTemplate;

    @CheckForNull
    public abstract String getIconPath();

    @CheckForNull
    public abstract String getEnvVariableName();

    @CheckForNull
    public abstract String getDefaultName();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    /**
     * @return the {@link LogStorageRetriever} of this backend if the backend is configured to retrieve logs. {@code null} otherwise.
     */
    @CheckForNull
    @MustBeClosed
    public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
        return null;
    }

    /**
     * For extensions
     */
    public abstract Map<String, Object> mergeBindings(Map<String, Object> bindings);

    /**
     * Returns the specified backend name or the default name. Not {@code null} or empty.
     */
    public String getName() {
        return Strings.isNullOrEmpty(name) ? getDefaultName() : name;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return {@code null} if no {@link #getTraceVisualisationUrlTemplate()} has been defined or if the {@link #getMetricsVisualizationUrlTemplate} has a syntax error
     */
    @CheckForNull
    public String getTraceVisualisationUrl(Map<String, Object> bindings) {
        if (Strings.isNullOrEmpty(this.getTraceVisualisationUrlTemplate())) {
            return null;
        }
        if (traceVisualisationUrlGTemplate == ERROR_TEMPLATE) {
            return null;
        } else if (this.traceVisualisationUrlGTemplate == null) {
            GStringTemplateEngine gStringTemplateEngine = new GStringTemplateEngine();
            try {
                this.traceVisualisationUrlGTemplate =
                        gStringTemplateEngine.createTemplate(this.getTraceVisualisationUrlTemplate());
            } catch (IOException | ClassNotFoundException e) {
                LOGGER.log(
                        Level.WARNING,
                        "Invalid Trace Visualisation URL Template '" + this.getTraceVisualisationUrlTemplate() + "'",
                        e);
                this.traceVisualisationUrlGTemplate = ERROR_TEMPLATE;
            }
        }

        Map<String, Object> mergedBindings = mergeBindings(bindings);
        return traceVisualisationUrlGTemplate.make(mergedBindings).toString();
    }

    public String getMetricsVisualizationUrl(Resource resource) {
        if (Strings.isNullOrEmpty(this.getMetricsVisualizationUrlTemplate())) {
            return null;
        }
        if (metricsVisualizationUrlGTemplate == ERROR_TEMPLATE) {
            return null;
        } else if (this.metricsVisualizationUrlGTemplate == null) {
            GStringTemplateEngine gStringTemplateEngine = new GStringTemplateEngine();
            try {
                this.metricsVisualizationUrlGTemplate =
                        gStringTemplateEngine.createTemplate(this.getMetricsVisualizationUrlTemplate());
            } catch (IOException | ClassNotFoundException e) {
                LOGGER.log(
                        Level.WARNING,
                        "Invalid Metrics Visualisation URL Template '" + this.getMetricsVisualizationUrlTemplate()
                                + "'",
                        e);
                this.metricsVisualizationUrlGTemplate = ERROR_TEMPLATE;
            }
        }
        Map<String, String> resourceMap = resource.getAttributes().asMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().getKey(),
                        entry -> OtelUtils.urlEncode(Objects.toString(entry.getValue()))));
        Map<String, Object> mergedBindings = mergeBindings(Collections.singletonMap("resource", resourceMap));
        try {
            return this.metricsVisualizationUrlGTemplate.make(mergedBindings).toString();
        } catch (MissingPropertyException e) {
            this.metricsVisualizationUrlGTemplate = ERROR_TEMPLATE;
            LOGGER.log(
                    Level.WARNING,
                    "Failure to generate MetricsVisualizationUrl, missing binding for property '" + e.getProperty()
                            + "' in template " + getMetricsVisualizationUrlTemplate());
            return null;
        }
    }

    @Override
    public abstract Map<String, Object> getBindings();

    @SuppressWarnings("unchecked")
    @Override
    public Descriptor<ObservabilityBackend> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * Returns all the registered {@link ObservabilityBackend} descriptors. Used by the GUI
     */
    public static DescriptorExtensionList<ObservabilityBackend, ObservabilityBackendDescriptor> allDescriptors() {
        return Jenkins.get().getDescriptorList(ObservabilityBackend.class);
    }

    /**
     * Extension point for Observability backends to contribute to the configuration properties used to instantiate the OpenTelemetry SDK.
     */
    @NonNull
    public Map<String, String> getOtelConfigurationProperties() {
        return Collections.emptyMap();
    }

    /**
     * List the attribute keys of the template bindings exposed by {@link ObservabilityBackend#getBindings()}
     */
    public interface TemplateBindings {
        String BACKEND_NAME = "backendName";
        String BACKEND_24_24_ICON_URL = "backend24x24IconUrl";

        String SERVICE_NAME = "serviceName";
        String SERVICE_NAMESPACE = "serviceNamespace";
        String SERVICE_NAMESPACE_AND_NAME = "serviceNamespaceAndName";

        String ROOT_SPAN_NAME = "rootSpanName";
        String TRACE_ID = "traceId";
        String SPAN_ID = "spanId";
        /**
         * As {@link Instant}
         */
        String START_TIME = "startTime";

        /**
         * As {@link Instant}
         */
        String END_TIME = "endTime";
    }

    public abstract static class ObservabilityBackendDescriptor extends Descriptor<ObservabilityBackend>
            implements Comparable<ObservabilityBackendDescriptor> {
        /**
         * Enable displaying the {@link CustomObservabilityBackend} at the end when listing all available backend types
         *
         * @return ordinal position
         */
        public int ordinal() {
            return 0;
        }

        @Override
        public int compareTo(ObservabilityBackendDescriptor o) {
            return ComparisonChain.start()
                    .compare(this.ordinal(), o.ordinal())
                    .compare(this.getDisplayName(), o.getDisplayName())
                    .result();
        }
    }

    public static final Template ERROR_TEMPLATE;

    static {
        try {
            ERROR_TEMPLATE = new GStringTemplateEngine().createTemplate("#ERROR#");
        } catch (Exception e) {
            throw new IllegalStateException("failure to create error template");
        }
    }
}
