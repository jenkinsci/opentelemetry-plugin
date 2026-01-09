/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.job.log;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticLogsBackendWithJenkinsVisualization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ElasticStackConfigurationFormTest {

    @SuppressWarnings("unused")
    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule r) {
        this.r = r;
    }

    @Test
    void testDoCheckKibanaUrl() {
        ElasticBackend.DescriptorImpl config = new ElasticBackend.DescriptorImpl();
        assertEquals(FormValidation.Kind.OK, config.doCheckKibanaBaseUrl("http://example.com:100").kind);
        assertEquals(FormValidation.Kind.OK, config.doCheckKibanaBaseUrl("").kind);
        assertEquals(FormValidation.Kind.ERROR, config.doCheckKibanaBaseUrl("foo").kind);
    }

    @Test
    void testDoCheckElasticsearchUrl() {
        ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl config =
                new ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl();
        assertEquals(FormValidation.Kind.OK, config.doCheckElasticsearchUrl("http://example.com:1000").kind);
        assertEquals(FormValidation.Kind.OK, config.doCheckElasticsearchUrl("").kind);
        assertEquals(FormValidation.Kind.ERROR, config.doCheckElasticsearchUrl("foo").kind);
    }
}
