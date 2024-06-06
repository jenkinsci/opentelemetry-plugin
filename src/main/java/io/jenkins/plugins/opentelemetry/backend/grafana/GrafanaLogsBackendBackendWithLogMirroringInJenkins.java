/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * Store pipeline logs both in Loki and in the Jenkins home directory. Visualization of logs is done retrieving logs
 * from the Jenkins home directory.
 */
public class GrafanaLogsBackendBackendWithLogMirroringInJenkins extends GrafanaLogsBackend {
    @DataBoundConstructor
    public GrafanaLogsBackendBackendWithLogMirroringInJenkins() {
    }

    /**
     * Logs should be retrieved from the Jenkins home, not from Loki
     *
     * @return {@code null}
     */
    @Override
    public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
        return null;
    }

    /**
     * Activate the log mirroring to disk in addition to activation of the OTel export of pipeline logs done by the
     * {@link GrafanaLogsBackend#getOtelConfigurationProperties()}
     */
    public Map<String, String> getOtelConfigurationProperties() {
        Map<String, String> properties = new HashMap<>(super.getOtelConfigurationProperties());
        properties.put("otel.logs.mirror_to_disk", Boolean.TRUE.toString());
        return properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return NoGrafanaLogsBackend.class.hashCode();
    }

    @Extension(ordinal = 100)
    public static class DescriptorImpl extends GrafanaLogsBackend.DescriptorImpl {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Store pipeline logs in Loki and mirror them in Jenkins";
        }
    }
}
