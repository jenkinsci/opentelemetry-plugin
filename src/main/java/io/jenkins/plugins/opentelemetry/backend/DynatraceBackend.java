/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DynatraceBackend extends ObservabilityBackend {

    public static final String OTEL_DYNATRACE_URL = "OTEL_DYNATRACE_URL";
    public static final String DEFAULT_NAME = "Dynatrace";
    private final String url;
    private String managementZoneId;

    private String dashboardId;
    private String dashboardTimeRange;

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
    public DynatraceBackend(String url) {
        if (url != null && !url.endsWith("/")) {
            url = url + "/";
        }
        this.url = url;
    }

    public String getManagementZoneId() {
        return managementZoneId;
    }

    @DataBoundSetter
    public void setManagementZoneId(String managementZoneId) {
        this.managementZoneId = managementZoneId;
    }

    public String getDashboardId() {
        return dashboardId;
    }

    @DataBoundSetter
    public void setDashboardId(String dashboardId) {
        this.dashboardId = dashboardId;
    }

    public String getDashboardTimeRange() {
        return dashboardTimeRange;
    }

    @DataBoundSetter
    public void setDashboardTimeRange(String dashboardTimeRange) {
        this.dashboardTimeRange = dashboardTimeRange;
    }

    @Override
    public Map<String, Object> mergeBindings(Map<String, Object> bindings) {
        Map<String, Object> mergedBindings = new HashMap<>(bindings);
        mergedBindings.put("dynatraceBaseUrl", this.url);
        String zoneId = Util.fixEmpty(getManagementZoneId()) != null ? getManagementZoneId() : "all";
        mergedBindings.put("managementZoneId", zoneId);

        mergedBindings.put("dashboardId", Util.fixEmpty(dashboardId));

        String timeRange = Util.fixEmpty(getDashboardTimeRange()) != null ? getDashboardTimeRange() : "today";
        mergedBindings.put("dashboardTimeRange", Util.fixEmpty(timeRange));

        return mergedBindings;
    }

    @NonNull
    @Override
    public String getTraceVisualisationUrlTemplate() {
        return "${dynatraceBaseUrl}#trace;gf=${managementZoneId};traceId=${traceId}";
    }

    @Override
    @CheckForNull
    public String getMetricsVisualizationUrlTemplate() {
        if (Util.fixEmpty(getDashboardId()) == null) {
            return null;
        }

        return "${dynatraceBaseUrl}#dashboard;id=${dashboardId};gf=${managementZoneId};gtf=${dashboardTimeRange}";
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
        DynatraceBackend that = (DynatraceBackend) o;
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
