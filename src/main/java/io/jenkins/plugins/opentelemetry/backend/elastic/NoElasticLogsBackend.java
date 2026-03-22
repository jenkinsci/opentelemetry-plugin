/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import java.util.Collections;
import java.util.Map;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Logs backend implementation that disables Elasticsearch log storage.
 */
public class NoElasticLogsBackend extends ElasticLogsBackend {
    /**
     * Creates the no-op Elasticsearch logs backend configuration.
     */
    @DataBoundConstructor
    public NoElasticLogsBackend() {}

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
     * @return {@code true} when {@code o} is also a {@code NoElasticLogsBackend}
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
        return NoElasticLogsBackend.class.hashCode();
    }

    /**
     * Descriptor for the no-op Elasticsearch logs backend.
     */
    @Extension(ordinal = 100)
    public static class DescriptorImpl extends ElasticLogsBackend.DescriptorImpl {
        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "Don't store pipeline logs in Elastic";
        }
    }
}
