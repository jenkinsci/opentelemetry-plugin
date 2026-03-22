/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * ObservabilityBackend for the Jaeger distributed tracing platform.
 */
public class JaegerBackend extends ObservabilityBackend {

    /** Environment variable name used to pass the Jaeger base URL to build agents. */
    public static final String OTEL_JAEGER_URL = "OTEL_JAEGER_URL";
    /** Default display name for the Jaeger backend. */
    public static final String DEFAULT_NAME = "Jaeger";

    private String jaegerBaseUrl;

    static {
        IconSet.icons.addIcon(new Icon("icon-otel-jaeger icon-sm", ICONS_PREFIX + "jaeger.svg", Icon.ICON_SMALL_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel-jaeger icon-md", ICONS_PREFIX + "jaeger.svg", Icon.ICON_MEDIUM_STYLE));
        IconSet.icons.addIcon(new Icon("icon-otel-jaeger icon-lg", ICONS_PREFIX + "jaeger.svg", Icon.ICON_LARGE_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel-jaeger icon-xlg", ICONS_PREFIX + "jaeger.svg", Icon.ICON_XLARGE_STYLE));
    }

    /**
     * Creates Jaeger backend configuration with defaults.
     */
    @DataBoundConstructor
    public JaegerBackend() {}

    /**
     * Merges provided bindings with Jaeger-specific bindings.
     *
     * @param bindings base bindings
     * @return merged bindings
     */
    @Override
    public Map<String, Object> mergeBindings(Map<String, Object> bindings) {
        Map<String, Object> mergedBindings = new HashMap<>(bindings);
        mergedBindings.put("jaegerBaseUrl", this.jaegerBaseUrl);
        return mergedBindings;
    }

    /**
     * Returns URL template that opens a trace in Jaeger UI.
     *
     * @return trace visualization URL template
     */
    @CheckForNull
    @Override
    public String getTraceVisualisationUrlTemplate() {
        return "${jaegerBaseUrl}/trace/${traceId}";
    }

    /**
     * Returns configured Jaeger base URL.
     *
     * @return Jaeger base URL
     */
    public String getJaegerBaseUrl() {
        return jaegerBaseUrl;
    }

    /**
     * Sets Jaeger base URL.
     *
     * @param jaegerBaseUrl Jaeger base URL
     */
    @DataBoundSetter
    public void setJaegerBaseUrl(String jaegerBaseUrl) {
        this.jaegerBaseUrl = jaegerBaseUrl;
    }

    /**
     * Returns the icon CSS class for the Jaeger backend.
     *
     * @return icon CSS class
     */
    @CheckForNull
    @Override
    public String getIconPath() {
        return "icon-otel-jaeger";
    }

    /**
     * Returns the environment variable name for the Jaeger URL.
     *
     * @return environment variable name
     */
    @CheckForNull
    @Override
    public String getEnvVariableName() {
        return OTEL_JAEGER_URL;
    }

    /**
     * Returns the default display name for this backend.
     *
     * @return default name
     */
    @CheckForNull
    @Override
    public String getDefaultName() {
        return DEFAULT_NAME;
    }

    /**
     * Returns {@code null} because Jaeger does not provide a metrics visualization URL.
     *
     * @return {@code null}
     */
    @CheckForNull
    @Override
    public String getMetricsVisualizationUrlTemplate() {
        return null;
    }

    /**
     * Compares backend configuration values.
     *
     * @param obj object to compare
     * @return {@code true} when the object is also a {@link JaegerBackend}
     */
    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof JaegerBackend;
    }

    /**
     * Returns hash code for backend configuration.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return JaegerBackend.class.hashCode();
    }

    /**
     * Returns template bindings contributed by this backend.
     *
     * @return Jaeger-specific bindings
     */
    @Override
    public Map<String, Object> getBindings() {
        return Map.of(
                ObservabilityBackend.TemplateBindings.BACKEND_NAME,
                getName(),
                ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL,
                "/plugin/opentelemetry/images/24x24/jaeger.png");
    }

    /**
     * Descriptor for the Jaeger observability backend.
     */
    @Extension
    @Symbol("jaeger")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {
        /**
         * Returns display name used in Jenkins backend selector.
         *
         * @return backend display name
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return DEFAULT_NAME;
        }

        /**
         * Validates Jaeger base URL entered in global configuration.
         *
         * @param jaegerBaseUrl user-provided Jaeger base URL
         * @return validation result
         */
        public FormValidation doCheckJaegerBaseUrl(@QueryParameter String jaegerBaseUrl) {
            if (jaegerBaseUrl == null || jaegerBaseUrl.isEmpty()) {
                return FormValidation.ok();
            }
            try {
                new URL(jaegerBaseUrl);
            } catch (MalformedURLException e) {
                return FormValidation.error("Invalid URL: " + e.getMessage());
            }
            return FormValidation.ok();
        }
    }
}
