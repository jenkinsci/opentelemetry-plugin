/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import hudson.util.FormValidation;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend.DescriptorImpl;
import io.jenkins.plugins.opentelemetry.rules.CheckIsDockerAvailable;
import io.jenkins.plugins.opentelemetry.rules.CheckIsLinuxOrMac;
import jenkins.model.GlobalConfiguration;

public class ElasticStackConfigurationITTest {
    @ClassRule
    @ConfiguredWithCode("jcasc-elastic-backend.yml")
    public static JenkinsConfiguredWithCodeRule jenkinsRule = new JenkinsConfiguredWithCodeRule();
    
    @ClassRule
    public static Timeout globalTimeout = new Timeout(10, TimeUnit.MINUTES);
    
    @ClassRule
    public static CheckIsLinuxOrMac isLinuxOrMac = new CheckIsLinuxOrMac();
    
    @ClassRule
    public static CheckIsDockerAvailable isDockerAvailable = new CheckIsDockerAvailable();
    
    public ElasticStack elasticsearch;
    private ElasticBackend.DescriptorImpl descriptorBackend;
    private ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl descriptorVisualization;

    @Before
    public void setUp() throws Exception {
        elasticsearch = new ElasticStack();
        elasticsearch.start();
        final JenkinsOpenTelemetryPluginConfiguration configuration = GlobalConfiguration.all()
                .get(JenkinsOpenTelemetryPluginConfiguration.class);
        ElasticBackend elasticBackendConfiguration = (ElasticBackend) configuration.getObservabilityBackends().get(0);
        ElasticLogsBackendWithJenkinsVisualization elasticStackConfiguration = ((ElasticLogsBackendWithJenkinsVisualization) elasticBackendConfiguration
                .getElasticLogsBackend());
        descriptorBackend = (DescriptorImpl) elasticBackendConfiguration.getDescriptor();
        descriptorVisualization = (io.jenkins.plugins.opentelemetry.backend.elastic.ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl) elasticStackConfiguration
                .getDescriptor();
    }

    @Test
    public void testCredentialsDoValidate() {
        assertEquals(FormValidation.Kind.OK, descriptorBackend.doCheckKibanaBaseUrl("http://kibana.example.com").kind);
        assertEquals(FormValidation.Kind.OK, descriptorVisualization.doValidate(elasticsearch.getEsUrl(), true, ElasticStack.CRED_ID).kind);

        assertEquals(FormValidation.Kind.ERROR, descriptorVisualization.doValidate(elasticsearch.getEsUrl(), true, ElasticStack.WRONG_CREDS).kind);
        assertEquals(FormValidation.Kind.ERROR, descriptorVisualization.doValidate("nowhere", true, ElasticStack.CRED_ID).kind);
    }

    @Test
    public void testDoFillCredentialsIdItems() {
        assertFalse(descriptorVisualization.doFillElasticsearchCredentialsIdItems(null, ElasticStack.CRED_ID).isEmpty());
    }

    @Test
    public void testDoCheckCredentialsId() {
        assertEquals(FormValidation.Kind.OK, descriptorVisualization.doCheckElasticsearchCredentialsId(null, ElasticStack.CRED_ID).kind);
        assertEquals(FormValidation.Kind.ERROR, descriptorVisualization.doCheckElasticsearchCredentialsId(null, "foo").kind);
    }
}
