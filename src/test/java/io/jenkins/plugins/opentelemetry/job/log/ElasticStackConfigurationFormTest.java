/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.job.log;

import static junit.framework.TestCase.assertEquals;

import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticLogsBackendWithJenkinsVisualization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class ElasticStackConfigurationFormTest {

    @SuppressWarnings("unused")
    private JenkinsRule r;

    @BeforeEach
    public void setUp(JenkinsRule r) {
        this.r = r;
    }

    @Test
    public void testDoCheckKibanaUrl() {
        ElasticBackend.DescriptorImpl config = new ElasticBackend.DescriptorImpl();
        assertEquals(config.doCheckKibanaBaseUrl("http://example.com:100").kind, FormValidation.Kind.OK);
        assertEquals(config.doCheckKibanaBaseUrl("").kind, FormValidation.Kind.OK);
        assertEquals(config.doCheckKibanaBaseUrl("foo").kind, FormValidation.Kind.ERROR);
    }

    @Test
    public void testDoCheckElasticsearchUrl() {
        ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl config =
                new ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl();
        assertEquals(config.doCheckElasticsearchUrl("http://example.com:1000").kind, FormValidation.Kind.OK);
        assertEquals(config.doCheckElasticsearchUrl("").kind, FormValidation.Kind.OK);
        assertEquals(config.doCheckElasticsearchUrl("foo").kind, FormValidation.Kind.ERROR);
    }
}
