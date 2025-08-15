/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.job.log;

import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticLogsBackendWithJenkinsVisualization;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ElasticStackConfigurationFormTest {

    @Test
    void testDoCheckKibanaUrl() {
        ElasticBackend.DescriptorImpl config = new ElasticBackend.DescriptorImpl();
        Assertions.assertEquals(FormValidation.Kind.OK, config.doCheckKibanaBaseUrl("http://example.com:100").kind);
        Assertions.assertEquals(FormValidation.Kind.OK, config.doCheckKibanaBaseUrl("").kind);
        Assertions.assertEquals(FormValidation.Kind.ERROR, config.doCheckKibanaBaseUrl("foo").kind);
    }

    @Test
    void testDoCheckElasticsearchUrl() {
        ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl config =
                new ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl();
        Assertions.assertEquals(FormValidation.Kind.OK, config.doCheckElasticsearchUrl("http://example.com:1000").kind);
        Assertions.assertEquals(FormValidation.Kind.OK, config.doCheckElasticsearchUrl("").kind);
        Assertions.assertEquals(FormValidation.Kind.ERROR, config.doCheckElasticsearchUrl("foo").kind);
    }
}
