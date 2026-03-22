/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.GrafanaBackend;
import io.jenkins.plugins.opentelemetry.backend.custom.CustomLogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import java.util.Map;
import java.util.Objects;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class GrafanaLogsBackendWithoutJenkinsVisualization extends GrafanaLogsBackend
        implements TemplateBindingsProvider {

    private String grafanaLokiDatasourceIdentifier = GrafanaBackend.DEFAULT_LOKI_DATA_SOURCE_IDENTIFIER;

    /**
     * Creates Grafana Loki logs backend configuration with defaults.
     */
    @DataBoundConstructor
    public GrafanaLogsBackendWithoutJenkinsVisualization() {}

    /**
     * Returns Grafana Loki datasource identifier.
     *
     * @return Loki datasource identifier
     */
    public String getGrafanaLokiDatasourceIdentifier() {
        return grafanaLokiDatasourceIdentifier;
    }

    /**
     * Sets Grafana Loki datasource identifier.
     *
     * @param grafanaLokiDatasourceIdentifier Loki datasource identifier
     */
    @DataBoundSetter
    public void setGrafanaLokiDatasourceIdentifier(String grafanaLokiDatasourceIdentifier) {
        this.grafanaLokiDatasourceIdentifier = grafanaLokiDatasourceIdentifier;
    }

    /**
     * Creates a log storage retriever using the configured Loki Grafana datasource.
     *
     * @param templateBindingsProvider template bindings provider
     * @return Grafana log storage retriever
     */
    @Override
    @MustBeClosed
    public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
        return new CustomLogStorageRetriever(getBuildLogsVisualizationUrlTemplate(), templateBindingsProvider);
    }

    /**
     * Compares backend configuration values for equality.
     *
     * @param o object to compare
     * @return {@code true} when configurations are equivalent
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GrafanaLogsBackendWithoutJenkinsVisualization that = (GrafanaLogsBackendWithoutJenkinsVisualization) o;
        return Objects.equals(grafanaLokiDatasourceIdentifier, that.grafanaLokiDatasourceIdentifier);
    }

    /**
     * Returns hash code for backend configuration.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(grafanaLokiDatasourceIdentifier);
    }

    /**
     * Returns string representation for diagnostics.
     *
     * @return printable backend configuration
     */
    @Override
    public String toString() {
        return "GrafanaLogsBackendWithoutJenkinsVisualization{" + "grafanaLokiDatasourceIdentifier='"
                + grafanaLokiDatasourceIdentifier + '\'' + '}';
    }

    /**
     * Returns template variable bindings for Grafana Loki.
     *
     * @return bindings map with Loki datasource identifier
     */
    @Override
    public Map<String, Object> getBindings() {
        return Map.of(
                GrafanaBackend.TemplateBindings.GRAFANA_LOKI_DATASOURCE_IDENTIFIER,
                getGrafanaLokiDatasourceIdentifier());
    }

    @Extension(ordinal = 50)
    public static class DescriptorImpl extends GrafanaLogsBackend.DescriptorImpl {
        /**
         * Returns the default Loki datasource identifier.
         *
         * @return default Loki datasource identifier
         */
        @NonNull
        public String getDefaultLokiDataSourceIdentifier() {
            return GrafanaBackend.DEFAULT_LOKI_DATA_SOURCE_IDENTIFIER;
        }

        /**
         * Returns the default Loki OpenTelemetry log format.
         *
         * @return default Loki OTel log format name
         */
        @Override
        public String getDefaultLokiOTelLogFormat() {
            return LokiOTelLogFormat.LOKI_V3_OTEL_FORMAT.name();
        }

        /**
         * Returns display name used in global configuration.
         *
         * @return logs backend display name
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "Store pipeline logs In Loki and visualize logs exclusively in Grafana (logs no longer visible through Jenkins screens)";
        }
    }
}
