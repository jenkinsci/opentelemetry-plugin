/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.grafana.GrafanaLogsBackend;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class GrafanaBackend extends ObservabilityBackend {

    /** Default display name for the Grafana backend. */
    public static final String DEFAULT_BACKEND_NAME = "Grafana";

    /** Environment variable name used to pass the Grafana base URL to the agent. */
    public static final String OTEL_GRAFANA_URL = "OTEL_GRAFANA_URL";

    private static final String DEFAULT_TEMPO_DATA_SOURCE_IDENTIFIER = "grafanacloud-traces";
    /** Default Loki data source identifier used when none is configured. */
    public static final String DEFAULT_LOKI_DATA_SOURCE_IDENTIFIER = "grafanacloud-logs";

    private static final String DEFAULT_GRAFANA_ORG_ID = "1";

    private static final String DEFAULT_TEMPO_QUERY_TYPE = "traceql";

    static {
        IconSet.icons.addIcon(
                new Icon("icon-otel-grafana icon-sm", ICONS_PREFIX + "grafana.svg", Icon.ICON_SMALL_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel-grafana icon-md", ICONS_PREFIX + "grafana.svg", Icon.ICON_MEDIUM_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel-grafana icon-lg", ICONS_PREFIX + "grafana.svg", Icon.ICON_LARGE_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel-grafana icon-xlg", ICONS_PREFIX + "grafana.svg", Icon.ICON_XLARGE_STYLE));
    }

    private String grafanaBaseUrl;

    private String grafanaMetricsDashboard;
    private String tempoDataSourceIdentifier = DEFAULT_TEMPO_DATA_SOURCE_IDENTIFIER;

    private String grafanaOrgId = DEFAULT_GRAFANA_ORG_ID;

    private String tempoQueryType = DEFAULT_TEMPO_QUERY_TYPE;

    private GrafanaLogsBackend grafanaLogsBackend;

    /**
     * Creates Grafana backend configuration with defaults.
     */
    @DataBoundConstructor
    public GrafanaBackend() {}

    /**
     * Returns URL template to open a trace in Grafana Explore.
     *
     * @return trace visualization URL template
     */
    @Nullable
    @Override
    public String getTraceVisualisationUrlTemplate() {
        return "${" + TemplateBindings.GRAFANA_BASE_URL + "}" + "/explore?orgId="
                + "${"
                + TemplateBindings.GRAFANA_ORG_ID + "}" + "&left=%7B%22datasource%22:%22"
                + "${"
                + TemplateBindings.GRAFANA_TEMPO_DATASOURCE_IDENTIFIER + "}"
                + "%22,%22queries%22:%5B%7B%22refId%22:%22A%22,%22datasource%22:%7B%22type%22:%22tempo%22,%22uid%22:%22"
                + "${"
                + TemplateBindings.GRAFANA_TEMPO_DATASOURCE_IDENTIFIER + "}" + "%22%7D,%22queryType%22:%22${"
                + TemplateBindings.GRAFANA_TEMPO_QUERY_TYPE + "}%22,%22query%22:%22" + "${traceId}"
                + "%22%7D%5D,%22range%22:%7B%22from%22:%22"
                + "${"
                + TemplateBindings.START_TIME
                + ".minusSeconds(600).atZone(java.util.TimeZone.getDefault().toZoneId()).toInstant().toEpochMilli()}"
                + "%22,%22to%22:%22"
                + "${"
                + TemplateBindings.START_TIME
                + ".plusSeconds(600).atZone(java.util.TimeZone.getDefault().toZoneId()).toInstant().toEpochMilli()}"
                + "%22%7D%7D";
    }

    /**
     * Not yet instrumented
     */
    @Nullable
    @Override
    public String getMetricsVisualizationUrlTemplate() {
        return grafanaMetricsDashboard;
    }

    /**
     * Returns the icon path for this Grafana backend.
     *
     * @return icon identifier string
     */
    @Nullable
    @Override
    public String getIconPath() {
        return "icon-otel-grafana";
    }

    /**
     * Returns the environment variable name used to pass the Grafana URL to the agent.
     *
     * @return environment variable name
     */
    @Nullable
    @Override
    public String getEnvVariableName() {
        return OTEL_GRAFANA_URL;
    }

    /**
     * Returns the default display name for this backend.
     *
     * @return default backend name
     */
    @Nullable
    @Override
    public String getDefaultName() {
        return DEFAULT_BACKEND_NAME;
    }

    /**
     * Compares backend configuration values.
     *
     * @param o object to compare
     * @return {@code true} when configurations are equivalent
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GrafanaBackend that = (GrafanaBackend) o;
        return Objects.equals(grafanaOrgId, that.grafanaOrgId)
                && Objects.equals(grafanaBaseUrl, that.grafanaBaseUrl)
                && Objects.equals(tempoDataSourceIdentifier, that.tempoDataSourceIdentifier)
                && Objects.equals(tempoQueryType, that.tempoQueryType);
    }

    /**
     * Returns hash code for backend configuration.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(grafanaBaseUrl, tempoDataSourceIdentifier, grafanaOrgId, tempoQueryType);
    }

    /**
     * Merges provided template bindings with backend-specific bindings.
     *
     * @param bindings base bindings
     * @return merged bindings
     */
    @Override
    public Map<String, Object> mergeBindings(Map<String, Object> bindings) {
        Map<String, Object> mergedBindings = new HashMap<>(bindings);
        mergedBindings.putAll(getBindings());
        return mergedBindings;
    }

    /**
     * Returns template bindings used to render Grafana links.
     *
     * @return Grafana-specific bindings
     */
    @Override
    public Map<String, Object> getBindings() {
        Map<String, Object> bindings = Map.of(
                TemplateBindings.BACKEND_NAME, getName(),
                TemplateBindings.BACKEND_24_24_ICON_URL, "/plugin/opentelemetry/images/24x24/grafana.png",
                TemplateBindings.GRAFANA_BASE_URL, this.getGrafanaBaseUrl(),
                TemplateBindings.GRAFANA_ORG_ID, String.valueOf(this.getGrafanaOrgId()),
                TemplateBindings.GRAFANA_TEMPO_DATASOURCE_IDENTIFIER, this.getTempoDataSourceIdentifier(),
                TemplateBindings.GRAFANA_TEMPO_QUERY_TYPE, this.getTempoQueryType());

        if (grafanaLogsBackend instanceof TemplateBindingsProvider) {
            Map<String, Object> logsBackendBindings = ((TemplateBindingsProvider) grafanaLogsBackend).getBindings();
            Map<String, Object> result = new HashMap<>(bindings);
            result.putAll(logsBackendBindings);
            return result;
        } else {
            return bindings;
        }
    }

    /**
     * Creates a log storage retriever that delegates to the configured Grafana logs backend.
     *
     * @param templateBindingsProvider provider for template bindings used to construct log query URLs
     * @return a log storage retriever, or {@code null} when no logs backend is configured
     */
    @CheckForNull
    @Override
    public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
        return Optional.ofNullable(grafanaLogsBackend)
                .map(b -> b.newLogStorageRetriever(templateBindingsProvider))
                .orElse(null);
    }

            /**
             * Returns configured Grafana base URL.
             *
             * @return Grafana base URL
             */
    public String getGrafanaBaseUrl() {
        return grafanaBaseUrl;
    }

            /**
             * Sets Grafana base URL.
             *
             * @param grafanaBaseUrl Grafana base URL
             */
    @DataBoundSetter
    public void setGrafanaBaseUrl(String grafanaBaseUrl) {
        this.grafanaBaseUrl = grafanaBaseUrl;
    }

            /**
             * Returns Tempo data source identifier.
             *
             * @return Tempo data source identifier
             */
    @DataBoundSetter
    public String getTempoDataSourceIdentifier() {
        return tempoDataSourceIdentifier;
    }

            /**
             * Sets Tempo data source identifier.
             *
             * @param tempoDataSourceIdentifier Tempo data source identifier
             */
    @DataBoundSetter
    public void setTempoDataSourceIdentifier(String tempoDataSourceIdentifier) {
        this.tempoDataSourceIdentifier = tempoDataSourceIdentifier;
    }

            /**
             * Sets Grafana metrics dashboard URL.
             *
             * @param grafanaMetricsDashboard metrics dashboard URL
             */
    @DataBoundSetter
    public void setGrafanaMetricsDashboard(String grafanaMetricsDashboard) {
        this.grafanaMetricsDashboard = grafanaMetricsDashboard;
    }

            /**
             * Returns Grafana organization identifier.
             *
             * @return Grafana org id
             */
    public String getGrafanaOrgId() {
        return grafanaOrgId;
    }

            /**
             * Sets Grafana organization identifier.
             *
             * @param grafanaOrgId Grafana org id
             */
    @DataBoundSetter
    public void setGrafanaOrgId(String grafanaOrgId) {
        this.grafanaOrgId = grafanaOrgId;
    }

            /**
             * Returns Tempo query type.
             *
             * @return Tempo query type
             */
    @DataBoundSetter
    public String getTempoQueryType() {
        return tempoQueryType;
    }

            /**
             * Sets Tempo query type.
             *
             * @param tempoQueryType Tempo query type
             */
    @DataBoundSetter
    public void setTempoQueryType(String tempoQueryType) {
        this.tempoQueryType = tempoQueryType;
    }

            /**
             * Returns nested Grafana logs backend configuration.
             *
             * @return Grafana logs backend, or {@code null} when unset
             */
    @CheckForNull
    public GrafanaLogsBackend getGrafanaLogsBackend() {
        return grafanaLogsBackend;
    }

            /**
             * Sets nested Grafana logs backend configuration.
             *
             * @param grafanaLogsBackend Grafana logs backend
             */
    @DataBoundSetter
    public void setGrafanaLogsBackend(GrafanaLogsBackend grafanaLogsBackend) {
        this.grafanaLogsBackend = grafanaLogsBackend;
    }

    /**
     * Returns OTel SDK configuration properties contributed by the Grafana logs backend.
     *
     * @return a map of OTel configuration properties, or an empty map when no logs backend is set
     */
    @NonNull
    @Override
    public Map<String, String> getOtelConfigurationProperties() {
        if (grafanaLogsBackend == null) {
            return Collections.emptyMap();
        } else {
            return grafanaLogsBackend.getOtelConfigurationProperties();
        }
    }

    /** Descriptor for the Grafana backend configuration in the Jenkins UI. */
    @Extension
    @Symbol("grafana")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {

        /**
         * Returns display name used in Jenkins backend selector.
         *
         * @return backend display name
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return DEFAULT_BACKEND_NAME;
        }

        /**
         * Returns default Grafana organization identifier.
         *
         * @return default Grafana org id
         */
        public String getDefaultGrafanaOrgId() {
            return DEFAULT_GRAFANA_ORG_ID;
        }

        /**
         * Returns default Tempo data source identifier.
         *
         * @return default Tempo data source identifier
         */
        public String getDefaultTempoDataSourceIdentifier() {
            return DEFAULT_TEMPO_DATA_SOURCE_IDENTIFIER;
        }

        /**
         * Returns default Tempo query type.
         *
         * @return default Tempo query type
         */
        public String getDefaultTempoQueryType() {
            return DEFAULT_TEMPO_QUERY_TYPE;
        }

        /**
         * Validates Grafana base URL entered in global configuration.
         *
         * @param grafanaBaseUrl user-provided Grafana base URL
         * @return validation result
         */
        public FormValidation doCheckGrafanaBaseUrl(@QueryParameter("grafanaBaseUrl") String grafanaBaseUrl) {
            if (StringUtils.isEmpty(grafanaBaseUrl)) {
                return FormValidation.ok();
            }
            try {
                new URI(grafanaBaseUrl).toURL();
            } catch (URISyntaxException | MalformedURLException e) {
                return FormValidation.error("Invalid URL: " + e.getMessage());
            }
            return FormValidation.ok();
        }

        /**
         * Returns available Tempo query types for configuration UI.
         *
         * @return list box model with supported query types
         */
        public ListBoxModel doFillTempoQueryTypeItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("Query Tempo using TraceQL", "traceql");
            items.add("Query Tempo by TraceID (older Tempo versions)", "traceid");
            return items;
        }
    }

    /**
     * List the attribute keys of the template bindings exposed by {@link ObservabilityBackend#getBindings()}
     */
    public interface TemplateBindings extends ObservabilityBackend.TemplateBindings {
        String GRAFANA_BASE_URL = "grafanaBaseUrl";
        String GRAFANA_TEMPO_DATASOURCE_IDENTIFIER = "grafanaTempoDatasourceIdentifier";
        String GRAFANA_LOKI_DATASOURCE_IDENTIFIER = "grafanaLokiDatasourceIdentifier";
        String GRAFANA_ORG_ID = "grafanaOrgId";
        String GRAFANA_TEMPO_QUERY_TYPE = "grafanaTempoQueryType";
    }
}
