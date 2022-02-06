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
    public ElasticsearchContainer esContainer = new ElasticsearchContainer();
    private ElasticBackend elasticStackConfiguration;
    private ElasticBackend.DescriptorImpl descriptor;

    @BeforeClass
    public static void requiresDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }

    @Before
    public void setUp() throws Exception {
        elasticStackConfiguration = ExtensionList.lookupSingleton(ElasticBackend.class);
        descriptor = ((ElasticBackend.DescriptorImpl) elasticStackConfiguration.getDescriptor());
        SystemCredentialsProvider.getInstance().getCredentials().add(
            new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, CRED_ID, "", ElasticsearchContainer.USER_NAME,
                ElasticsearchContainer.PASSWORD
            ));
        SystemCredentialsProvider.getInstance().getCredentials().add(
            new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, WRONG_CREDS, "", "foo", "bar"));
        elasticStackConfiguration.setElasticsearchCredentialsId(CRED_ID);
        elasticStackConfiguration.setElasticsearchUrl(esContainer.getUrl());
        esContainer.createLogIndex();
    }

    @Test
    public void testCredentialsDoValidate() {
        assertEquals(descriptor.doValidate(CRED_ID, esContainer.getUrl(), ElasticsearchContainer.INDEX_PATTERN).kind, FormValidation.Kind.OK);

        assertEquals(descriptor.doValidate(WRONG_CREDS, esContainer.getUrl(), ElasticsearchContainer.INDEX_PATTERN).kind,
            FormValidation.Kind.ERROR
        );
        assertEquals(descriptor.doValidate(CRED_ID, "nowhere", ElasticsearchContainer.INDEX_PATTERN).kind, FormValidation.Kind.ERROR);
    }

    @Test
    public void testIndexPatternDoValidate()  {
        assertEquals(FormValidation.Kind.OK, descriptor.doValidate(CRED_ID, esContainer.getUrl(), ElasticsearchContainer.INDEX_PATTERN).kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doValidate(CRED_ID, esContainer.getUrl(), "pattern").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doValidate(CRED_ID, esContainer.getUrl(), "").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doValidate(CRED_ID, "", "pattern").kind);
    }

    @Test
    public void testDoFillCredentialsIdItems() {
        assertFalse(descriptor.doFillElasticsearchCredentialsIdItems(null, CRED_ID).isEmpty());
    }

    @Test
    public void testDoCheckCredentialsId() {
        assertEquals(descriptor.doCheckElasticsearcCredentialsId(null, CRED_ID).kind, FormValidation.Kind.OK);
        assertEquals(descriptor.doCheckElasticsearcCredentialsId(null, "foo").kind, FormValidation.Kind.WARNING);
    }

    @Test
    public void testDoCheckIndexPattern() {
        assertEquals(descriptor.doCheckIndexPattern("foo").kind, FormValidation.Kind.OK);
        assertEquals(descriptor.doCheckIndexPattern("").kind, FormValidation.Kind.WARNING);
    }
}
