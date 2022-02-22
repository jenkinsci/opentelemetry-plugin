/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.job.log;

import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticLogsBackendWithJenkinsVisualization;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class ElasticStackConfigurationFormTest {

    @Test
    public void testDoCheckKibanaUrl() {
        ElasticBackend.DescriptorImpl config = new ElasticBackend.DescriptorImpl();
        assertEquals(config.doCheckKibanaUrl("http://example.com:100").kind, FormValidation.Kind.OK);
        assertEquals(config.doCheckKibanaUrl("").kind, FormValidation.Kind.OK);
        assertEquals(config.doCheckKibanaUrl("foo").kind, FormValidation.Kind.ERROR);
    }

    @Test
    public void testDoCheckElasticsearchUrl() {
        ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl config = new ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl();
        assertEquals(config.doCheckElasticsearchUrl("http://example.com:1000").kind, FormValidation.Kind.OK);
        assertEquals(config.doCheckElasticsearchUrl("").kind, FormValidation.Kind.OK);
        assertEquals(config.doCheckElasticsearchUrl("foo").kind, FormValidation.Kind.ERROR);
    }
}
