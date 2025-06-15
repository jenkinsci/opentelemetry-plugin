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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DynatraceGrailBackend extends ObservabilityBackend {

    public static final String OTEL_DYNATRACE_URL = "OTEL_DYNATRACE_URL";
    public static final String DEFAULT_NAME = "Dynatrace (Grail)";
    private final String url;

    static {
        IconSet.icons.addIcon(
                new Icon(
                        "icon-otel-dynatrace icon-sm",
                        ICONS_PREFIX + "dynatrace.svg",
                        Icon.ICON_SMALL_STYLE));
        IconSet.icons.addIcon(
                new Icon(
                        "icon-otel-dynatrace icon-md",
                        ICONS_PREFIX + "dynatrace.svg",
                        Icon.ICON_MEDIUM_STYLE));
        IconSet.icons.addIcon(
                new Icon(
                        "icon-otel-dynatrace icon-lg",
                        ICONS_PREFIX + "dynatrace.svg",
                        Icon.ICON_LARGE_STYLE));
        IconSet.icons.addIcon(
                new Icon(
                        "icon-otel-dynatrace icon-xlg",
                        ICONS_PREFIX + "dynatrace.svg",
                        Icon.ICON_XLARGE_STYLE));
    }

    @DataBoundConstructor
    public DynatraceGrailBackend(String url) {
        if (url != null && !url.endsWith("/")) {
            url = url + "/";
        }
        this.url = url;
    }

    @Override
    public Map<String, Object> mergeBindings(Map<String, Object> bindings) {
        Map<String, Object> mergedBindings = new HashMap<>(bindings);
        mergedBindings.put("dynatraceBaseUrl", this.url);
        return mergedBindings;
    }

    @NonNull
    @Override
    public String getTraceVisualisationUrlTemplate() {
        // all jenkins traces
        // "ui/apps/dynatrace.distributedtracing/explorer?v=spans&filter=dt.entity.service.entity.name+%3D+jenkins"
        return "${dynatraceBaseUrl}ui/apps/dynatrace.distributedtracing/explorer?v=spans&filter=trace.id+%3D+${traceId}&traceId=${traceId}&cv=a%2Cfalse&sidebar=a%2Cfalse";
    }

    @Override
    @CheckForNull
    public String getMetricsVisualizationUrlTemplate() {

        String filtersAndColumns = "ui/apps/dynatrace.distributedtracing/explorer?" +
                "filter=ci.pipeline.name+%3D+*+OR+jenkins.pipeline.step.name+%3D+*&" +
                "columns=start_time%2Cspan.name%2Cduration%2Crequest.status_code%2Cci.pipeline.name%2Cci.pipeline.run.cause%2Cci.pipeline.run.durationMillis%2Cjenkins.pipeline.step.plugin.name%2Cjenkins.pipeline.step.name%2Cjenkins.pipeline.step.result%2Cci.pipeline.run.user%2Cjenkins.url&sidebar=u%2Cfalse&v=spans&tf=-7d%3Bnow";
        return "${dynatraceBaseUrl}" + filtersAndColumns;
    }

    public String getUrl() {
        return url;
    }

    @NonNull
    @Override
    public String getIconPath() {
        return "icon-otel-dynatrace";
    }

    @NonNull
    @Override
    public String getEnvVariableName() {
        return OTEL_DYNATRACE_URL;
    }

    @NonNull
    @Override
    public String getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DynatraceGrailBackend that = (DynatraceGrailBackend) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public Map<String, Object> getBindings() {
        return Map.of(
                ObservabilityBackend.TemplateBindings.BACKEND_NAME, getName(),
                ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL,
                "/plugin/opentelemetry/images/svgs/dynatrace.svg");
    }

    @Extension
    @Symbol("dynatrace")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {
        @Override
        @NonNull
        public String getDisplayName() {
            return DEFAULT_NAME;
        }
    }
}
