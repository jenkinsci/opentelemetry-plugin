/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.Map;

public class NoElasticLogsBackend extends ElasticLogsBackend {
    @DataBoundConstructor
    public NoElasticLogsBackend() {
    }

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
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return NoElasticLogsBackend.class.hashCode();
    }

    @Extension(ordinal = 100)
    public static class DescriptorImpl extends ElasticLogsBackend.DescriptorImpl {
        @Override
        public String getDisplayName() {
            return "Don't store pipeline logs in Elastic";
        }
    }
}
