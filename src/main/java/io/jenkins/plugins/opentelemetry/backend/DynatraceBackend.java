/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class DynatraceBackend extends ObservabilityBackend {

    /** Environment variable name used to pass the Dynatrace base URL to the agent. */
    public static final String OTEL_DYNATRACE_URL = "OTEL_DYNATRACE_URL";
    /** Default display name for the Dynatrace backend. */
    public static final String DEFAULT_NAME = "Dynatrace";
    private final String url;
    private String managementZoneId;

    private String dashboardId;
    private String dashboardTimeRange;

    static {
        IconSet.icons.addIcon(
                new Icon("icon-otel-dynatrace icon-sm", ICONS_PREFIX + "dynatrace.svg", Icon.ICON_SMALL_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel-dynatrace icon-md", ICONS_PREFIX + "dynatrace.svg", Icon.ICON_MEDIUM_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel-dynatrace icon-lg", ICONS_PREFIX + "dynatrace.svg", Icon.ICON_LARGE_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel-dynatrace icon-xlg", ICONS_PREFIX + "dynatrace.svg", Icon.ICON_XLARGE_STYLE));
    }

    /**
     * Creates Dynatrace backend configuration with the given Dynatrace environment URL.
     * A trailing slash is appended automatically when absent.
     *
     * @param url Dynatrace base URL
     */
    @DataBoundConstructor
    public DynatraceBackend(String url) {
        if (url != null && !url.endsWith("/")) {
            url = url + "/";
        }
        this.url = url;
    }

    /**
     * Returns the configured Dynatrace management zone ID.
     *
     * @return management zone ID, or {@code null} when not configured
     */
    public String getManagementZoneId() {
        return managementZoneId;
    }

    /**
     * Sets the Dynatrace management zone ID.
     *
     * @param managementZoneId management zone ID
     */
    @DataBoundSetter
    public void setManagementZoneId(String managementZoneId) {
        this.managementZoneId = managementZoneId;
    }

    /**
     * Returns the configured Dynatrace dashboard ID.
     *
     * @return dashboard ID, or {@code null} when not configured
     */
    public String getDashboardId() {
        return dashboardId;
    }

    /**
     * Sets the Dynatrace dashboard ID.
     *
     * @param dashboardId dashboard ID
     */
    @DataBoundSetter
    public void setDashboardId(String dashboardId) {
        this.dashboardId = dashboardId;
    }

    /**
     * Returns configured dashboard time range.
     *
     * @return dashboard time range, or {@code null} when not configured
     */
    public String getDashboardTimeRange() {
        return dashboardTimeRange;
    }

    /**
     * Sets dashboard time range.
     *
     * @param dashboardTimeRange dashboard time range expression
     */
    @DataBoundSetter
    public void setDashboardTimeRange(String dashboardTimeRange) {
        this.dashboardTimeRange = dashboardTimeRange;
    }

    /**
     * Merges default bindings with Dynatrace-specific template bindings.
     *
     * @param bindings base bindings
     * @return merged bindings including Dynatrace URL, zone, and dashboard settings
     */
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

    /**
     * Returns the URL template for opening a trace in the Dynatrace distributed tracing UI.
     *
     * @return trace visualization URL template
     */
    @NonNull
    @Override
    public String getTraceVisualisationUrlTemplate() {
        return "${dynatraceBaseUrl}#trace;gf=${managementZoneId};traceId=${traceId}";
    }

    /**
     * Returns the URL template for opening the Dynatrace metrics dashboard, or {@code null}
     * when no dashboard ID is configured.
     *
     * @return metrics dashboard URL template, or {@code null}
     */
    @Override
    @CheckForNull
    public String getMetricsVisualizationUrlTemplate() {
        if (Util.fixEmpty(getDashboardId()) == null) {
            return null;
        }

        return "${dynatraceBaseUrl}#dashboard;id=${dashboardId};gf=${managementZoneId};gtf=${dashboardTimeRange}";
    }

    /**
     * Returns configured Dynatrace base URL.
     *
     * @return Dynatrace base URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the icon path for this Dynatrace backend.
     *
     * @return icon identifier string
     */
    @NonNull
    @Override
    public String getIconPath() {
        return "icon-otel-dynatrace";
    }

    /**
     * Returns the environment variable name used to pass the Dynatrace URL to the agent.
     *
     * @return environment variable name
     */
    @NonNull
    @Override
    public String getEnvVariableName() {
        return OTEL_DYNATRACE_URL;
    }

    /**
     * Returns the default display name for this backend.
     *
     * @return default backend name
     */
    @NonNull
    @Override
    public String getDefaultName() {
        return DEFAULT_NAME;
    }

    /**
     * Compares Dynatrace backends by URL.
     *
     * @param o object to compare
     * @return {@code true} when URLs match
     */
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

    /**
     * Returns hash code consistent with {@link #equals(Object)}.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    /**
     * Returns template bindings exposed by this backend.
     *
     * @return backend bindings map
     */
    @Override
    public Map<String, Object> getBindings() {
        return Map.of(
                ObservabilityBackend.TemplateBindings.BACKEND_NAME,
                getName(),
                ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL,
                "/plugin/opentelemetry/images/svgs/dynatrace.svg");
    }

    /** Descriptor for the Dynatrace backend configuration in the Jenkins UI. */
    @Extension
    @Symbol("dynatrace")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {
        /**
         * Returns descriptor display name for Jenkins configuration UI.
         *
         * @return descriptor display name
         */
        @Override
        @NonNull
        public String getDisplayName() {
            return DEFAULT_NAME;
        }
    }
