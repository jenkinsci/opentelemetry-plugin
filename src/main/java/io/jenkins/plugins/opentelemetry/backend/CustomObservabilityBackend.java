/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Map;
import java.util.Objects;

public class CustomObservabilityBackend extends ObservabilityBackend {

    public static final String OTEL_CUSTOM_URL = "OTEL_CUSTOM_URL";
    public static final String DEFAULT_NAME = "Custom Observability Backend";
    /**
     * TODO fix typo "visualisation" -> "visualization" but WARNING handle backward compatibility
     */
    private String traceVisualisationUrlTemplate;
    private String metricsVisualizationUrlTemplate;

    @DataBoundConstructor
    public CustomObservabilityBackend() {

    }

    @DataBoundSetter
    public void setTraceVisualisationUrlTemplate(String traceVisualisationUrlTemplate) {
        this.traceVisualisationUrlTemplate = traceVisualisationUrlTemplate;
    }

    @DataBoundSetter
    public void setMetricsVisualizationUrlTemplate(String metricsVisualizationUrlTemplate) {
        this.metricsVisualizationUrlTemplate = metricsVisualizationUrlTemplate;
    }

    @Override
    public Map<String, Object> mergeBindings(Map<String, Object> bindings) {
        return bindings;
    }

    @CheckForNull
    @Override
    public String getTraceVisualisationUrlTemplate() {
        return this.traceVisualisationUrlTemplate;
    }

    @CheckForNull
    @Override
    public String getMetricsVisualizationUrlTemplate() {
        return this.metricsVisualizationUrlTemplate;
    }

    @CheckForNull
    @Override
    public String getIconPath() {
        return "icon-monitor";
    }

    @CheckForNull
    @Override
    public String getEnvVariableName() {
        return OTEL_CUSTOM_URL;
    }

    @CheckForNull
    @Override
    public String getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    public String toString() {
        return "CustomBackend{" +
                "traceVisualisationUrlTemplate='" + traceVisualisationUrlTemplate + '\'' +
                ", metricsVisualizationUrl='" + metricsVisualizationUrlTemplate + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomObservabilityBackend that = (CustomObservabilityBackend) o;
        return Objects.equals(traceVisualisationUrlTemplate, that.traceVisualisationUrlTemplate) && Objects.equals(metricsVisualizationUrlTemplate, that.metricsVisualizationUrlTemplate);
    }

    @Override
    public int hashCode() {
        return super.generateHashCode(traceVisualisationUrlTemplate, metricsVisualizationUrlTemplate);
    }

    @Override
    public Map<String, Object> getBindings() {
        return Map.of(
            ObservabilityBackend.TemplateBindings.BACKEND_NAME, getName(),
            ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL, "/plugin/opentelemetry/images/svgs/opentelemetry.svg"
        );
    }

    @Extension
    @Symbol("customObservabilityBackend")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {
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
