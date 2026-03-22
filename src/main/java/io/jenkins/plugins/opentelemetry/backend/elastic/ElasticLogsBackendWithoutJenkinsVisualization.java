/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import com.google.errorprone.annotations.MustBeClosed;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.custom.CustomLogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Elastic logs backend that stores logs in Elasticsearch and visualizes them exclusively in Elastic
 * (build logs are not visible through Jenkins screens).
 */
public class ElasticLogsBackendWithoutJenkinsVisualization extends ElasticLogsBackend {

    /**
     * Creates an Elastic logs backend (without Jenkins visualization) configuration.
     */
    @DataBoundConstructor
    public ElasticLogsBackendWithoutJenkinsVisualization() {}

    /**
     * Creates a log storage retriever that delegates log visualization to Elastic.
     *
     * @param templateBindingsProvider template bindings provider
     * @return custom log storage retriever backed by Elastic
     */
    @Override
    @MustBeClosed
    public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
        return new CustomLogStorageRetriever(getBuildLogsVisualizationUrlTemplate(), templateBindingsProvider);
    }

    /**
     * Compares backends by type identity.
     *
     * @param o object to compare
     * @return {@code true} when {@code o} is also an {@code ElasticLogsBackendWithoutJenkinsVisualization}
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
        return ElasticLogsBackendWithoutJenkinsVisualization.class.hashCode();
    }

    /**
     * Returns a debug-friendly string representation.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "ElasticLogsBackendWithoutJenkinsVisualization{" + '}';
    }

    /**
     * Descriptor for the Elastic-only visualization logs backend.
     */
    @Extension(ordinal = 50)
    public static class DescriptorImpl extends ElasticLogsBackend.DescriptorImpl {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Store pipeline logs In Elastic and visualize logs exclusively in Elastic (logs no longer visible through Jenkins screens)";
        }
    }
}
