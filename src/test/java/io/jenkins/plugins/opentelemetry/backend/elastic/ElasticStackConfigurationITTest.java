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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.testcontainers.DockerClientFactory;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

@Ignore
public class ElasticStackConfigurationITTest {

    public static final String WRONG_CREDS = "wrongCreds";
    public static final String CRED_ID = "credID";
    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public ElasticsearchContainer esContainer = new ElasticsearchContainer();
    private ElasticBackend elasticStackConfiguration;
    private ElasticBackend.DescriptorImpl descriptor;

    //@BeforeClass
    //public static void requiresDocker() {
    //    assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    //}

//    @Before
//    public void setUp() throws Exception {
//        elasticStackConfiguration = ExtensionList.lookupSingleton(ElasticBackend.class);
//        descriptor = ((ElasticBackend.DescriptorImpl) elasticStackConfiguration.getDescriptor());
//        SystemCredentialsProvider.getInstance().getCredentials().add(
//            new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, CRED_ID, "", ElasticsearchContainer.USER_NAME,
//                ElasticsearchContainer.PASSWORD
//            ));
//        SystemCredentialsProvider.getInstance().getCredentials().add(
//            new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, WRONG_CREDS, "", "foo", "bar"));
//        elasticStackConfiguration.setElasticsearchCredentialsId(CRED_ID);
//        elasticStackConfiguration.setElasticsearchUrl(esContainer.getUrl());
//        esContainer.createLogIndex();
//    }
//
    @Test
    public void testCredentialsDoValidate() {
//        String kibanaBaseUrl = "http://kibana.example.com"; // FIXME get kibana url
//        assertEquals(descriptor.doValidate(CRED_ID, esContainer.getUrl(), kibanaBaseUrl).kind, FormValidation.Kind.OK);
//
//        assertEquals(descriptor.doValidate(WRONG_CREDS, esContainer.getUrl(), kibanaBaseUrl).kind,
//            FormValidation.Kind.ERROR
//        );
//        assertEquals(descriptor.doValidate(CRED_ID, "nowhere", kibanaBaseUrl).kind, FormValidation.Kind.ERROR);
    }
//
    @Test
    public void testIndexPatternDoValidate()  {
//        String kibanaBaseUrl = "http://kibana.example.com"; // FIXME get kibana url
//        assertEquals(FormValidation.Kind.OK, descriptor.doValidate(CRED_ID, esContainer.getUrl(), kibanaBaseUrl).kind);
//        assertEquals(FormValidation.Kind.ERROR, descriptor.doValidate(CRED_ID, esContainer.getUrl(), "pattern").kind);
//        assertEquals(FormValidation.Kind.ERROR, descriptor.doValidate(CRED_ID, esContainer.getUrl(), "").kind);
//        assertEquals(FormValidation.Kind.ERROR, descriptor.doValidate(CRED_ID, "", "pattern").kind);
    }

    @Test
    public void testDoFillCredentialsIdItems() {
//        assertFalse(descriptor.doFillElasticsearchCredentialsIdItems(null, CRED_ID).isEmpty());
    }

    @Test
    public void testDoCheckCredentialsId() {
//        assertEquals(descriptor.doCheckElasticsearchCredentialsId(null, CRED_ID).kind, FormValidation.Kind.OK);
//        assertEquals(descriptor.doCheckElasticsearchCredentialsId(null, "foo").kind, FormValidation.Kind.WARNING);
    }
}
