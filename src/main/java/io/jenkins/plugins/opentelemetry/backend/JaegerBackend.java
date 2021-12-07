/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import java.util.HashMap;
import java.util.Map;

@Extension
public class JaegerBackend extends ObservabilityBackend {

    public static final String OTEL_JAEGER_URL = "OTEL_JAEGER_URL";
    public static final String DEFAULT_NAME = "Jaeger";

	private String jaegerBaseUrl;

    @DataBoundConstructor
    public JaegerBackend(){

    }

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
        return "/plugin/opentelemetry/images/48x48/jaeger.png";
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

    @Extension
    @Symbol("jaeger")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {
        @Override
        public String getDisplayName() {
            return DEFAULT_NAME;
        }
    }
}
