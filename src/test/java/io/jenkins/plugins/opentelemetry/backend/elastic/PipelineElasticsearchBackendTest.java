/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import org.elasticsearch.action.search.SearchResponse;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.DockerComposeContainer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assume.assumeTrue;

public class PipelineElasticsearchBackendTest {

    public static final String CRED_ID = "credID";
    public static final int OTEL_PORT = 8200;
    public static final int KIBANA_PORT = 5601;
    public static final int ELASTICSEARCH_PORT = 9200;
    @ClassRule
    public static JenkinsConfiguredWithCodeRule jenkinsRule = new JenkinsConfiguredWithCodeRule();
    @ClassRule
    public static DockerComposeContainer environment = new DockerComposeContainer(
        new File("src/test/resources/docker-compose.yml"))
        .withExposedService("fleet-server_1", OTEL_PORT)
        .withExposedService("kibana_1", KIBANA_PORT)
        .withExposedService("elasticsearch_1", ELASTICSEARCH_PORT);
    static OpenTelemetrySdkProvider openTelemetrySdkProvider;
    private ElasticsearchRetriever elasticsearchRetriever;

    @BeforeClass
    public static void requiresDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }

    @Before
    public void setup() throws Exception {
        String otelEndpoint = "http://localhost:" + environment.getServicePort("fleet-server_1", OTEL_PORT);
        String esEndpoint = "http://localhost:" + environment.getServicePort("elasticsearch_1", ELASTICSEARCH_PORT);
        String kibanaEndpoint = "http://localhost:" + environment.getServicePort("kibana_1", KIBANA_PORT);

        SystemCredentialsProvider.getInstance().getCredentials().add(
            new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, CRED_ID, "", ElasticsearchContainer.USER_NAME, ElasticsearchContainer.PASSWORD)
        );

        JenkinsOpenTelemetryPluginConfiguration config = JenkinsOpenTelemetryPluginConfiguration.get();
        String properties = String.join(System.getProperty("line.separator"),
            "otel.traces.exporter=otlp",
            "otel.metrics.exporter=otlp",
            "otel.logs.exporter=otlp"
        );
        config.setConfigurationProperties(properties);
        config.setEndpoint(otelEndpoint);
        config.setExporterIntervalMillis(10);
        config.setServiceName("OtelJenkinsTest");
        config.setServiceNamespace("OtelLogTest");
        List<ObservabilityBackend> observabilityBackends = new ArrayList<>();
        ElasticBackend esBackend = new ElasticBackend();
        esBackend.setElasticsearchUrl(esEndpoint);
        esBackend.setIndexPattern(ElasticsearchContainer.INDEX_PATTERN);
        esBackend.setKibanaBaseUrl(kibanaEndpoint);
        esBackend.setElasticsearchCredentialsId(CRED_ID);
        observabilityBackends.add(esBackend);
        config.setObservabilityBackends(observabilityBackends);
        config.initializeOpenTelemetry();

        elasticsearchRetriever = new ElasticsearchRetriever(esEndpoint, ElasticsearchContainer.USER_NAME, ElasticsearchContainer.PASSWORD, ElasticsearchContainer.INDEX_PATTERN);
    }

    @Test
    public void test() throws Exception {
        jenkinsRule.createSlave("remote", null, null);
        WorkflowJob p = jenkinsRule.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node('remote') {\n" + "  echo 'Hello'\n" + "}", true));
        WorkflowRun run = jenkinsRule.buildAndAssertSuccess(p);
        waitForLogs(run);
        jenkinsRule.assertLogContains("Hello", run);
    }

    private void waitForLogs(WorkflowRun run) throws InterruptedException {
        long counter = 0;
        MonitoringAction action = run.getAction(MonitoringAction.class);
        String traceId = action.getTraceId();
        String spanId = action.getSpanId();
        do {
            try {
                SearchResponse searchResponse = elasticsearchRetriever.search(traceId, spanId);
                counter = searchResponse.getHits().getTotalHits().value;
            } catch (Throwable e) {
                //NOOP
            }
            Thread.sleep(1000);
        } while (counter < 10);
    }
}
