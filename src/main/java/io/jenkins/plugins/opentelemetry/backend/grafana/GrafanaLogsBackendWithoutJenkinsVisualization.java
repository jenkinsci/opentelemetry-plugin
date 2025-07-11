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

    @DataBoundConstructor
    public GrafanaLogsBackendWithoutJenkinsVisualization() {}

    public String getGrafanaLokiDatasourceIdentifier() {
        return grafanaLokiDatasourceIdentifier;
    }

    @DataBoundSetter
    public void setGrafanaLokiDatasourceIdentifier(String grafanaLokiDatasourceIdentifier) {
        this.grafanaLokiDatasourceIdentifier = grafanaLokiDatasourceIdentifier;
    }

    @Override
    @MustBeClosed
    public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
        return new CustomLogStorageRetriever(getBuildLogsVisualizationUrlTemplate(), templateBindingsProvider);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GrafanaLogsBackendWithoutJenkinsVisualization that = (GrafanaLogsBackendWithoutJenkinsVisualization) o;
        return Objects.equals(grafanaLokiDatasourceIdentifier, that.grafanaLokiDatasourceIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(grafanaLokiDatasourceIdentifier);
    }

    @Override
    public String toString() {
        return "GrafanaLogsBackendWithoutJenkinsVisualization{" + "grafanaLokiDatasourceIdentifier='"
                + grafanaLokiDatasourceIdentifier + '\'' + '}';
    }

    @Override
    public Map<String, Object> getBindings() {
        return Map.of(
                GrafanaBackend.TemplateBindings.GRAFANA_LOKI_DATASOURCE_IDENTIFIER,
                getGrafanaLokiDatasourceIdentifier());
    }

    @Extension(ordinal = 50)
    public static class DescriptorImpl extends GrafanaLogsBackend.DescriptorImpl {
        @NonNull
        public String getDefaultLokiDataSourceIdentifier() {
            return GrafanaBackend.DEFAULT_LOKI_DATA_SOURCE_IDENTIFIER;
        }

        @Override
        public String getDefaultLokiOTelLogFormat() {
            return LokiOTelLogFormat.LOKI_V3_OTEL_FORMAT.name();
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Store pipeline logs In Loki and visualize logs exclusively in Grafana (logs no longer visible through Jenkins screens)";
        }
    }
}
