/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
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

    public static final String WRONG_CREDS = "wrongCreds";
    public static final String CRED_ID = "credID";
    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public ElasticsearchContainer environment = new ElasticsearchContainer();
    private ElasticLogsBackendWithJenkinsVisualization elasticStackConfiguration;
    private ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl descriptor;
    private ElasticBackend elasticBackendConfiguration;
    private ElasticBackend.DescriptorImpl descriptorBackend;

    @BeforeClass
    public static void requiresDocker() {
       assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }

   @Before
   public void setUp() throws Exception {
       elasticBackendConfiguration = ExtensionList.lookupSingleton(ElasticBackend.class);
       descriptorBackend = ((ElasticBackend.DescriptorImpl) elasticBackendConfiguration.getDescriptor());
       elasticStackConfiguration = ExtensionList.lookupSingleton(ElasticLogsBackendWithJenkinsVisualization.class);
       descriptor = ((ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl) elasticStackConfiguration.getDescriptor());
       SystemCredentialsProvider.getInstance().getCredentials().add(
           new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, CRED_ID, "", ElasticsearchContainer.USER_NAME,
               ElasticsearchContainer.PASSWORD
           ));
       SystemCredentialsProvider.getInstance().getCredentials().add(
           new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, WRONG_CREDS, "", "foo", "bar"));
       elasticStackConfiguration.setElasticsearchCredentialsId(CRED_ID);
       elasticStackConfiguration.setElasticsearchUrl(environment.getEsUrl());
   }

    @Test
    public void testCredentialsDoValidate() {
       assertEquals(descriptorBackend.doCheckKibanaBaseUrl(environment.getKibanaUrl()).kind, FormValidation.Kind.OK);
       assertEquals(descriptor.doValidate(environment.getEsUrl(), true, CRED_ID).kind, FormValidation.Kind.OK);

       assertEquals(descriptor.doValidate(environment.getEsUrl(), true, WRONG_CREDS).kind,
           FormValidation.Kind.ERROR
       );
       assertEquals(descriptor.doValidate("nowhere", true, CRED_ID).kind, FormValidation.Kind.ERROR);
    }

    @Test
    public void testDoFillCredentialsIdItems() {
       assertFalse(descriptor.doFillElasticsearchCredentialsIdItems(null, CRED_ID).isEmpty());
    }

    @Test
    public void testDoCheckCredentialsId() {
       assertEquals(descriptor.doCheckElasticsearchCredentialsId(null, CRED_ID).kind, FormValidation.Kind.OK);
       assertEquals(descriptor.doCheckElasticsearchCredentialsId(null, "foo").kind, FormValidation.Kind.WARNING);
    }
}
