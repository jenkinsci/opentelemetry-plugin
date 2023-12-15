/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.testcontainers.DockerClientFactory;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

public class ElasticStackConfigurationITTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    /* TODO reimplement without Docker containers
    @Test
    public void testCredentialsDoValidate() {
        ElasticBackend.DescriptorImpl descriptorBackend = elasticStack.getDescriptorBackend();
        ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl descriptor = elasticStack.getDescriptor();

        assertEquals(descriptorBackend.doCheckKibanaBaseUrl(elasticStack.getKibanaUrl()).kind, FormValidation.Kind.OK);
        assertEquals(descriptor.doValidate(elasticStack.getEsUrl(), true, ElasticStack.CRED_ID).kind, FormValidation.Kind.OK);

        assertEquals(descriptor.doValidate(elasticStack.getEsUrl(), true, ElasticStack.WRONG_CREDS).kind,
                FormValidation.Kind.ERROR);
        assertEquals(descriptor.doValidate("nowhere", true, ElasticStack.CRED_ID).kind, FormValidation.Kind.ERROR);
    }

    @Test
    public void testDoFillCredentialsIdItems() {
        ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl descriptor = elasticStack.getDescriptor();
        assertFalse(descriptor.doFillElasticsearchCredentialsIdItems(null, ElasticStack.CRED_ID).isEmpty());
    }

    @Test
    public void testDoCheckCredentialsId() {
        ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl descriptor = elasticStack.getDescriptor();
        assertEquals(descriptor.doCheckElasticsearchCredentialsId(null, ElasticStack.CRED_ID).kind, FormValidation.Kind.OK);
        assertEquals(descriptor.doCheckElasticsearchCredentialsId(null, "foo").kind, FormValidation.Kind.WARNING);
    }
    */

}
