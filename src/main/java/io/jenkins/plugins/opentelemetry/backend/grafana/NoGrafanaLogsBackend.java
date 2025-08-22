/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import java.util.Collections;
import java.util.Map;
import org.kohsuke.stapler.DataBoundConstructor;

public class NoGrafanaLogsBackend extends GrafanaLogsBackend {
    @DataBoundConstructor
    public NoGrafanaLogsBackend() {}

    @Override
    public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
        return null;
    }

    public Map<String, String> getOtelConfigurationProperties() {
        return Collections.emptyMap();
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
        @NonNull
        @Override
        public String getDisplayName() {
            return "Don't store pipeline logs in Loki";
        }

        // doesn't matter what the default is, as this is not really a backend
        @Override
        public String getDefaultLokiOTelLogFormat() {
            return LokiOTelLogFormat.LOKI_V2_JSON_OTEL_FORMAT.name();
        }
    }
}
