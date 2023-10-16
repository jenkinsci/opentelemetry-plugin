/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import com.google.errorprone.annotations.MustBeClosed;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.GrafanaBackend;
import io.jenkins.plugins.opentelemetry.backend.custom.CustomLogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

public class GrafanaLogsBackendWithoutJenkinsVisualization extends GrafanaLogsBackend {

    private String grafanaLokiDatasourceIdentifier;

    @DataBoundConstructor
    public GrafanaLogsBackendWithoutJenkinsVisualization() {

    }

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
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return GrafanaLogsBackendWithoutJenkinsVisualization.class.hashCode();
    }


    @Override
    public String toString() {
        return "GrafanaLogsBackendWithoutJenkinsVisualization{" +
            '}';
    }

    @Extension(ordinal = 50)
    public static class DescriptorImpl extends GrafanaLogsBackend.DescriptorImpl {
        public String getDefaultLokiDataSourceIdentifier(){
            return GrafanaBackend.DEFAULT_LOKI_DATA_SOURCE_IDENTIFIER;
        }
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Store pipeline logs In Loki and visualize logs exclusively in Grafana (logs no longer visible through Jenkins screens)";
        }
    }
}
