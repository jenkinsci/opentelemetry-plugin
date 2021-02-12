/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import hudson.Extension;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import java.util.HashMap;
import java.util.Map;

@Extension
@Symbol("jaeger")
public class JaegerBackend extends ObservabilityBackend {

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
    public String getMetricsVisualisationUrlTemplate() {
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
    public static class DescriptorImpl extends Descriptor<ObservabilityBackend> {
        @Override
        public String getDisplayName() {
            return "Jaeger";
        }
    }

}
