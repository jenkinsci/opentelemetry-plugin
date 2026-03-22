/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Logs backend implementation that disables Loki log storage; pipeline logs are only stored in Jenkins.
 */
public class NoGrafanaLogsBackend extends GrafanaLogsBackend {
    /**
     * Creates the no-op Loki logs backend configuration.
     */
    @DataBoundConstructor
    public NoGrafanaLogsBackend() {}

    /**
     * Returns {@code null} because this backend does not store logs externally.
     *
     * @param templateBindingsProvider ignored
     * @return {@code null}
     */
    @Override
    public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
        return null;
    }

    /**
     * Returns an empty configuration properties map because no external system is configured.
     *
     * @return empty map
     */
    public Map<String, String> getOtelConfigurationProperties() {
        return Collections.emptyMap();
    }

    /**
     * Compares backends by type identity.
     *
     * @param o object to compare
     * @return {@code true} when {@code o} is also a {@code NoGrafanaLogsBackend}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    /**
     * Returns hash code based on class identity.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return NoGrafanaLogsBackend.class.hashCode();
    }

    /**
     * Descriptor for the no-op Loki logs backend.
     */
    @Extension(ordinal = 100)
    public static class DescriptorImpl extends GrafanaLogsBackend.DescriptorImpl {
        /**
         * {@inheritDoc}
         */
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Don't store pipeline logs in Loki";
        }

        // doesn't matter what the default is, as this is not really a backend
        /**
         * {@inheritDoc} Returns the default Loki OTel log format (unused for this backend).
         */
        @Override
        public String getDefaultLokiOTelLogFormat() {
            return LokiOTelLogFormat.LOKI_V2_JSON_OTEL_FORMAT.name();
        }
    }
}
