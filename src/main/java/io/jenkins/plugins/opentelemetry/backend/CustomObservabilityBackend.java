/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.Map;
import java.util.Objects;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class CustomObservabilityBackend extends ObservabilityBackend {

    public static final String OTEL_CUSTOM_URL = "OTEL_CUSTOM_URL";
    public static final String DEFAULT_NAME = "Custom Observability Backend";
    /**
     * TODO fix typo "visualisation" -> "visualization" but WARNING handle backward compatibility
     */
    private String traceVisualisationUrlTemplate;

    private String metricsVisualizationUrlTemplate;

    @DataBoundConstructor
    public CustomObservabilityBackend() {}

    /**
     * Sets the trace visualization URL template.
     *
     * @param traceVisualisationUrlTemplate Groovy template used to build trace links
     */
    @DataBoundSetter
    public void setTraceVisualisationUrlTemplate(String traceVisualisationUrlTemplate) {
        this.traceVisualisationUrlTemplate = traceVisualisationUrlTemplate;
    }

    /**
     * Sets the metrics visualization URL template.
     *
     * @param metricsVisualizationUrlTemplate Groovy template used to build metrics links
     */
    @DataBoundSetter
    public void setMetricsVisualizationUrlTemplate(String metricsVisualizationUrlTemplate) {
        this.metricsVisualizationUrlTemplate = metricsVisualizationUrlTemplate;
    }

    /**
     * Returns bindings unchanged because custom backend does not add extra template values.
     *
     * @param bindings base template bindings
     * @return the same bindings map
     */
    @Override
    public Map<String, Object> mergeBindings(Map<String, Object> bindings) {
        return bindings;
    }

    /**
     * Returns the configured trace visualization template.
     *
     * @return template string, or {@code null} when not configured
     */
    @CheckForNull
    @Override
    public String getTraceVisualisationUrlTemplate() {
        return this.traceVisualisationUrlTemplate;
    }

    /**
     * Returns the configured metrics visualization template.
     *
     * @return template string, or {@code null} when not configured
     */
    @CheckForNull
    @Override
    public String getMetricsVisualizationUrlTemplate() {
        return this.metricsVisualizationUrlTemplate;
    }

    /**
     * Returns the icon class used in Jenkins UI.
     *
     * @return icon class name
     */
    @CheckForNull
    @Override
    public String getIconPath() {
        return "icon-monitor";
    }

    /**
     * Returns the environment variable name associated with this backend URL.
     *
     * @return environment variable name
     */
    @CheckForNull
    @Override
    public String getEnvVariableName() {
        return OTEL_CUSTOM_URL;
    }

    /**
     * Returns the default backend display name.
     *
     * @return default backend name
     */
    @CheckForNull
    @Override
    public String getDefaultName() {
        return DEFAULT_NAME;
    }

    /**
     * Returns a debug-friendly representation of this backend.
     *
     * @return textual representation
     */
    @Override
    public String toString() {
        return "CustomBackend{" + "traceVisualisationUrlTemplate='"
                + traceVisualisationUrlTemplate + '\'' + ", metricsVisualizationUrl='"
                + metricsVisualizationUrlTemplate + '\'' + '}';
    }

    /**
     * Compares custom backends by their configured URL templates.
     *
     * @param o object to compare
     * @return {@code true} when templates match
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomObservabilityBackend that = (CustomObservabilityBackend) o;
        return Objects.equals(traceVisualisationUrlTemplate, that.traceVisualisationUrlTemplate)
                && Objects.equals(metricsVisualizationUrlTemplate, that.metricsVisualizationUrlTemplate);
    }

    /**
     * Returns hash code consistent with {@link #equals(Object)}.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(traceVisualisationUrlTemplate, metricsVisualizationUrlTemplate);
    }

    /**
     * Returns default template bindings exposed to custom backend templates.
     *
     * @return backend bindings map
     */
    @Override
    public Map<String, Object> getBindings() {
        return Map.of(
                ObservabilityBackend.TemplateBindings.BACKEND_NAME,
                getName(),
                ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL,
                "/plugin/opentelemetry/images/svgs/opentelemetry.svg");
    }

    @Extension
    @Symbol("customObservabilityBackend")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {
        /**
         * Returns descriptor display name for Jenkins configuration UI.
         *
         * @return descriptor display name
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return DEFAULT_NAME;
        }

        /**
         * Should be the last item when listing the observability backend types
         */
        @Override
        public int ordinal() {
            return 10;
        }
    }
}
