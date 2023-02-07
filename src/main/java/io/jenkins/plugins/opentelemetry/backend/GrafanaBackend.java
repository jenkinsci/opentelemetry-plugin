/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import hudson.Extension;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class GrafanaBackend extends ObservabilityBackend implements TemplateBindingsProvider {

    public static final String DEFAULT_BACKEND_NAME = "Grafana";

    public static final String OTEL_GRAFANA_URL = "OTEL_GRAFANA_URL";

    private static final String DEFAULT_TEMPO_DATA_SOURCE_IDENTIFIER = "grafanacloud-traces";
    private static final String DEFAULT_GRAFANA_ORG_ID = "1";

    static {
        IconSet.icons.addIcon(
            new Icon(
                "icon-otel-grafana icon-sm",
                ICONS_PREFIX + "grafana.svg",
                Icon.ICON_SMALL_STYLE));
        IconSet.icons.addIcon(
            new Icon(
                "icon-otel-grafana icon-md",
                ICONS_PREFIX + "grafana.svg",
                Icon.ICON_MEDIUM_STYLE));
        IconSet.icons.addIcon(
            new Icon(
                "icon-otel-grafana icon-lg",
                ICONS_PREFIX + "grafana.svg",
                Icon.ICON_LARGE_STYLE));
        IconSet.icons.addIcon(
            new Icon(
                "icon-otel-grafana icon-xlg",
                ICONS_PREFIX + "grafana.svg",
                Icon.ICON_XLARGE_STYLE));
    }

    private String grafanaBaseUrl;

    private String grafanaMetricsDashboard;
    private String tempoDataSourceIdentifier = DEFAULT_TEMPO_DATA_SOURCE_IDENTIFIER;

    private String grafanaOrgId = DEFAULT_GRAFANA_ORG_ID;

    @DataBoundConstructor
    public GrafanaBackend() {

    }

    @Nullable
    @Override
    public String getTraceVisualisationUrlTemplate() {
        return
            "${" + TemplateBindings.GRAFANA_BASE_URL + "}" +
                "/explore?orgId=" +
                "${" + TemplateBindings.GRAFANA_ORG_ID + "}" +
                "&left=%7B%22datasource%22:%22" +
                "${" + TemplateBindings.GRAFANA_TEMPO_DATASOURCE_IDENTIFIER + "}" +
                "%22,%22queries%22:%5B%7B%22refId%22:%22A%22,%22datasource%22:%7B%22type%22:%22tempo%22,%22uid%22:%22" +
                "${" + TemplateBindings.GRAFANA_TEMPO_DATASOURCE_IDENTIFIER + "}" +
                "%22%7D,%22queryType%22:%22traceId%22,%22query%22:%22" +
                "${traceId}" +
                "%22%7D%5D,%22range%22:%7B%22from%22:%22" +
                "${startTime.minusSeconds(600).atZone(java.util.TimeZone.getDefault().toZoneId()).toInstant().toEpochMilli()}" +
                "%22,%22to%22:%22" +
                "${startTime.plusSeconds(600).atZone(java.util.TimeZone.getDefault().toZoneId()).toInstant().toEpochMilli()}" +
                "%22%7D%7D";
    }

    /**
     * Not yet instrumented
     */
    @Nullable
    @Override
    public String getMetricsVisualizationUrlTemplate() {
        return grafanaMetricsDashboard;
    }

    @Nullable
    @Override
    public String getIconPath() {
        return "icon-otel-grafana";
    }

    @Nullable
    @Override
    public String getEnvVariableName() {
        return OTEL_GRAFANA_URL;
    }

    @Nullable
    @Override
    public String getDefaultName() {
        return DEFAULT_BACKEND_NAME;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GrafanaBackend that = (GrafanaBackend) o;
        return grafanaOrgId == that.grafanaOrgId && Objects.equals(grafanaBaseUrl, that.grafanaBaseUrl) && Objects.equals(tempoDataSourceIdentifier, that.tempoDataSourceIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(grafanaBaseUrl, tempoDataSourceIdentifier, grafanaOrgId);
    }

    @Override
    public Map<String, Object> mergeBindings(Map<String, Object> bindings) {
        Map<String, Object> mergedBindings = new HashMap<>(bindings);
        mergedBindings.putAll(getBindings());
        return mergedBindings;
    }

    @Override
    public Map<String, String> getBindings() {
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put(TemplateBindings.BACKEND_NAME, getName());
        bindings.put(ElasticBackend.TemplateBindings.BACKEND_24_24_ICON_URL, "/plugin/opentelemetry/images/24x24/grafana.png");

        bindings.put(TemplateBindings.GRAFANA_BASE_URL, this.getGrafanaBaseUrl());
        bindings.put(TemplateBindings.GRAFANA_ORG_ID, String.valueOf(this.getGrafanaOrgId()));
        bindings.put(TemplateBindings.GRAFANA_TEMPO_DATASOURCE_IDENTIFIER, this.getTempoDataSourceIdentifier());

        return bindings;
    }

    public String getGrafanaBaseUrl() {
        return grafanaBaseUrl;
    }

    @DataBoundSetter
    public void setGrafanaBaseUrl(String grafanaBaseUrl) {
        this.grafanaBaseUrl = grafanaBaseUrl;
    }

    @DataBoundSetter
    public String getTempoDataSourceIdentifier() {
        return tempoDataSourceIdentifier;
    }

    @DataBoundSetter
    public void setTempoDataSourceIdentifier(String tempoDataSourceIdentifier) {
        this.tempoDataSourceIdentifier = tempoDataSourceIdentifier;
    }

    @DataBoundSetter
    public void setGrafanaMetricsDashboard(String grafanaMetricsDashboard) {
        this.grafanaMetricsDashboard = grafanaMetricsDashboard;
    }

    public String getGrafanaOrgId() {
        return grafanaOrgId;
    }

    public void setGrafanaOrgId(String grafanaOrgId) {
        this.grafanaOrgId = grafanaOrgId;
    }

    @Extension
    @Symbol("grafana")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {

        @Override
        public String getDisplayName() {
            return DEFAULT_BACKEND_NAME;
        }

        public String getDefaultGrafanaOrgId() {
            return DEFAULT_GRAFANA_ORG_ID;
        }

        public String getDefaultTempoDataSourceIdentifier() {
            return DEFAULT_TEMPO_DATA_SOURCE_IDENTIFIER;
        }

        public FormValidation doCheckGrafanaBaseUrl(@QueryParameter("grafanaBaseUrl") String grafanaBaseUrl) {
            if (StringUtils.isEmpty(grafanaBaseUrl)) {
                return FormValidation.ok();
            }
            try {
                new URL(grafanaBaseUrl);
            } catch (MalformedURLException e) {
                return FormValidation.error("Invalid URL: " + e.getMessage());
            }
            return FormValidation.ok();
        }
    }

    /**
     * List the attribute keys of the template bindings exposed by {@link ObservabilityBackend#getBindings()}
     */
    public interface TemplateBindings extends ObservabilityBackend.TemplateBindings {
        String GRAFANA_BASE_URL = "grafanaBaseUrl";
        String GRAFANA_TEMPO_DATASOURCE_IDENTIFIER = "grafanaTempoDatasourceIdentifier";
        String GRAFANA_ORG_ID = "grafanaOrgId";
    }
}
