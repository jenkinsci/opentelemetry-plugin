/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.HashMap;
import java.util.Map;

public class ZipkinBackend extends ObservabilityBackend {

    public static final String OTEL_ZIPKIN_URL = "OTEL_ZIPKIN_URL";
    public static final String DEFAULT_NAME = "Zipkin";
	private String zipkinBaseUrl;

    static {
        IconSet.icons.addIcon(
            new Icon(
                "icon-otel-zipkin icon-sm",
                ICONS_PREFIX + "zipkin.svg",
                Icon.ICON_SMALL_STYLE));
        IconSet.icons.addIcon(
            new Icon(
                "icon-otel-zipkin icon-md",
                ICONS_PREFIX + "zipkin.svg",
                Icon.ICON_MEDIUM_STYLE));
        IconSet.icons.addIcon(
            new Icon(
                "icon-otel-zipkin icon-lg",
                ICONS_PREFIX + "zipkin.svg",
                Icon.ICON_LARGE_STYLE));
        IconSet.icons.addIcon(
            new Icon(
                "icon-otel-zipkin icon-xlg",
                ICONS_PREFIX + "zipkin.svg",
                Icon.ICON_XLARGE_STYLE));
    }

    @DataBoundConstructor
    public ZipkinBackend(){

    }

    @Override
    public Map<String, Object> mergeBindings(Map<String, Object> bindings) {
        Map<String, Object> mergedBindings = new HashMap<>(bindings);
        mergedBindings.put("zipkinBaseUrl", this.zipkinBaseUrl);
        return mergedBindings;
    }

    @CheckForNull
    @Override
    public String getTraceVisualisationUrlTemplate() {
        return "${zipkinBaseUrl}traces/${traceId}";
    }

    public String getZipkinBaseUrl() {
        return zipkinBaseUrl;
    }

    @DataBoundSetter
    public void setZipkinBaseUrl(String zipkinBaseUrl) {
        // warning, Zipkin gets wrong when using // like "http://localhost:9411/zipkin//traces/d8e42504c0a59489a5e3d2cb5da42662"
        if (zipkinBaseUrl != null && !zipkinBaseUrl.endsWith("/")){
            zipkinBaseUrl = zipkinBaseUrl + "/";
        }
        this.zipkinBaseUrl = zipkinBaseUrl;
    }

    @CheckForNull
    @Override
    public String getIconPath() {
        return "icon-otel-zipkin";
    }

    @CheckForNull
    @Override
    public String getEnvVariableName() {
        return OTEL_ZIPKIN_URL;
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
        return obj != null && obj instanceof ZipkinBackend;
    }

    @Override
    public int hashCode() {
        return super.generateHashCode(ZipkinBackend.class);
    }

    @Override
    public Map<String, Object> getBindings() {
        return Map.of(
            ObservabilityBackend.TemplateBindings.BACKEND_NAME, getName(),
            ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL, "/plugin/opentelemetry/images/24x24/zipkin.png");
    }

    @Extension
    @Symbol("zipkin")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return DEFAULT_NAME;
        }
    }
}
