/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.job.log;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.job.log.es.Retriever;
import org.elasticsearch.action.search.SearchResponse;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.DockerComposeContainer;

import java.io.File;

import static org.junit.Assume.assumeTrue;

public class PipelineTest {

    public static final String CRED_ID = "credID";
    @ClassRule
    public static DockerComposeContainer environment = new DockerComposeContainer(
        new File("src/test/resources/docker-compose.yml"));
    @Rule
    public JenkinsRule r = new JenkinsRule();
    private ElasticBackend elasticStackConfiguration;
    private ElasticBackend.DescriptorImpl descriptor;
    private Retriever retriever;

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
        elasticStackConfiguration.setElasticsearchUrl("http://localhost:5601");
        elasticStackConfiguration.setElasticsearcCredentialsId(CRED_ID);
        elasticStackConfiguration.setIndexPattern(ElasticsearchContainer.INDEX_PATTERN);

        retriever = new Retriever(elasticStackConfiguration.getElasticsearchUrl(), ElasticsearchContainer.USER_NAME,
            ElasticsearchContainer.PASSWORD, elasticStackConfiguration.getIndexPattern()
        );
    }

    @Test
    public void test() throws Exception {
        r.createSlave("remote", null, null);
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node('remote') {\n" + "  echo 'Hello'\n" + "}", true));
        WorkflowRun run = r.buildAndAssertSuccess(p);
        waitForLogs(run);
        r.assertLogContains("Hello", run);
    }

    private void waitForLogs(WorkflowRun run) {
        BuildInfo buildInfo = new BuildInfo(run.getFullDisplayName(), 1, null);
        long counter = 0;
        do {
            try {
                //FIXME check search string is correct
                SearchResponse searchResponse = retriever.search(buildInfo.getContext().get("KEY"));
                counter = searchResponse.getHits().getTotalHits().value;
            } catch (Throwable e) {
                //NOOP
            }
        } while (counter < 5);
    }
}
