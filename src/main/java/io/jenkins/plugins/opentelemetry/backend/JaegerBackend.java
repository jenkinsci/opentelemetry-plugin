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

public class JaegerBackend extends ObservabilityBackend {

    public static final String OTEL_JAEGER_URL = "OTEL_JAEGER_URL";
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

    @DataBoundConstructor
    public JaegerBackend() {}

    @Override
    public Map<String, Object> mergeBindings(Map<String, Object> bindings) {
        Map<String, Object> mergedBindings = new HashMap<>(bindings);
        mergedBindings.put("jaegerBaseUrl", this.jaegerBaseUrl);
        return mergedBindings;
    }

    @CheckForNull
    @Override
    public String getTraceVisualisationUrlTemplate() {
        return "${jaegerBaseUrl}/trace/${traceId}";
    }

    public String getJaegerBaseUrl() {
        return jaegerBaseUrl;
    }

    @DataBoundSetter
    public void setJaegerBaseUrl(String jaegerBaseUrl) {
        this.jaegerBaseUrl = jaegerBaseUrl;
    }

    @CheckForNull
    @Override
    public String getIconPath() {
        return "icon-otel-jaeger";
    }

    @CheckForNull
    @Override
    public String getEnvVariableName() {
        return OTEL_JAEGER_URL;
    }

    @CheckForNull
    @Override
    public String getDefaultName() {
        return DEFAULT_NAME;
    }

    @CheckForNull
    @Override
    public String getMetricsVisualizationUrlTemplate() {
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof JaegerBackend;
    }

    @Override
    public int hashCode() {
        return JaegerBackend.class.hashCode();
    }

    @Override
    public Map<String, Object> getBindings() {
        return Map.of(
                ObservabilityBackend.TemplateBindings.BACKEND_NAME,
                getName(),
                ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL,
                "/plugin/opentelemetry/images/24x24/jaeger.png");
    }

    @Extension
    @Symbol("jaeger")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return DEFAULT_NAME;
        }

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
