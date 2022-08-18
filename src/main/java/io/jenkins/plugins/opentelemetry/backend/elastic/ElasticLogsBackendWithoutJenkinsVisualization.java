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

public class ElasticLogsBackendWithoutJenkinsVisualization extends ElasticLogsBackend {

    @DataBoundConstructor
    public ElasticLogsBackendWithoutJenkinsVisualization() {

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
        return true;
    }

    @Override
    public int hashCode() {
        return ElasticLogsBackendWithoutJenkinsVisualization.class.hashCode();
    }


    @Override
    public String toString() {
        return "ElasticLogsBackendWithoutJenkinsVisualization{" +
            '}';
    }

    @Extension(ordinal = 50)
    public static class DescriptorImpl extends ElasticLogsBackend.DescriptorImpl {
        @Override
        public String getDisplayName() {
            return "Store pipeline logs In Elastic and visualize logs exclusively in Elastic (logs no longer visible through Jenkins screens)";
        }
    }
}
