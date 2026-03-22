/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.HashMap;
import java.util.Map;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class ZipkinBackend extends ObservabilityBackend {

    public static final String OTEL_ZIPKIN_URL = "OTEL_ZIPKIN_URL";
    public static final String DEFAULT_NAME = "Zipkin";
    private String zipkinBaseUrl;

    static {
        IconSet.icons.addIcon(new Icon("icon-otel-zipkin icon-sm", ICONS_PREFIX + "zipkin.svg", Icon.ICON_SMALL_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel-zipkin icon-md", ICONS_PREFIX + "zipkin.svg", Icon.ICON_MEDIUM_STYLE));
        IconSet.icons.addIcon(new Icon("icon-otel-zipkin icon-lg", ICONS_PREFIX + "zipkin.svg", Icon.ICON_LARGE_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel-zipkin icon-xlg", ICONS_PREFIX + "zipkin.svg", Icon.ICON_XLARGE_STYLE));
    }

    /**
     * Creates a Zipkin backend configuration with defaults.
     */
    @DataBoundConstructor
    public ZipkinBackend() {}

    /**
     * Merges Zipkin-specific bindings into existing template bindings.
     *
     * @param bindings existing bindings
     * @return merged bindings including Zipkin base URL
     */
    @Override
    public Map<String, Object> mergeBindings(Map<String, Object> bindings) {
        Map<String, Object> mergedBindings = new HashMap<>(bindings);
        mergedBindings.put("zipkinBaseUrl", this.zipkinBaseUrl);
        return mergedBindings;
    }

    /**
     * Returns Zipkin trace URL template.
     *
     * @return Zipkin trace URL template string
     */
    @CheckForNull
    @Override
    public String getTraceVisualisationUrlTemplate() {
        return "${zipkinBaseUrl}traces/${traceId}";
    }

    /**
     * Returns Zipkin base URL.
     *
     * @return Zipkin base URL
     */
    public String getZipkinBaseUrl() {
        return zipkinBaseUrl;
    }

    /**
     * Sets Zipkin base URL.
     *
     * @param zipkinBaseUrl Zipkin base URL
     */
    @DataBoundSetter
    public void setZipkinBaseUrl(String zipkinBaseUrl) {
        // warning, Zipkin gets wrong when using // like
        // "http://localhost:9411/zipkin//traces/d8e42504c0a59489a5e3d2cb5da42662"
        if (zipkinBaseUrl != null && !zipkinBaseUrl.endsWith("/")) {
            zipkinBaseUrl = zipkinBaseUrl + "/";
        }
        this.zipkinBaseUrl = zipkinBaseUrl;
    }

    /**
     * Returns icon CSS class name for Zipkin.
     *
     * @return icon CSS class name
     */
    @CheckForNull
    @Override
    public String getIconPath() {
        return "icon-otel-zipkin";
    }

    /**
     * Returns environment variable name for Zipkin endpoint.
     *
     * @return environment variable name
     */
    @CheckForNull
    @Override
    public String getEnvVariableName() {
        return OTEL_ZIPKIN_URL;
    }

    /**
     * Returns default display name for this backend.
     *
     * @return default backend name
     */
    @CheckForNull
    @Override
    public String getDefaultName() {
        return DEFAULT_NAME;
    }

    /**
     * Returns metrics visualization URL template.
     *
     * @return {@code null} — Zipkin does not support metrics visualization
     */
    @CheckForNull
    @Override
    public String getMetricsVisualizationUrlTemplate() {
        return null;
    }

    /**
     * Compares by backend type.
     *
     * @param obj object to compare
     * @return {@code true} when {@code obj} is a ZipkinBackend
     */
    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof ZipkinBackend;
    }

    /**
     * Returns hash code for this backend.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return ZipkinBackend.class.hashCode();
    }

    /**
     * Returns template variable bindings for Zipkin.
     *
     * @return bindings map with backend name and icon URL
     */
    @Override
    public Map<String, Object> getBindings() {
        return Map.of(
                ObservabilityBackend.TemplateBindings.BACKEND_NAME,
                getName(),
                ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL,
                "/plugin/opentelemetry/images/24x24/zipkin.png");
    }

    @Extension
    @Symbol("zipkin")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {
        /**
         * Returns display name used in global configuration.
         *
         * @return backend display name
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return DEFAULT_NAME;
        }
    }
}
