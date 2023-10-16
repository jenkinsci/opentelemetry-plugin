/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.DockerComposeContainer;

import java.io.File;
import java.time.Instant;

import static org.junit.Assume.assumeTrue;

@Ignore
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
    private ElasticsearchLogStorageRetriever elasticsearchRetriever;

    @BeforeClass
    public static void requiresDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }

//    @Before
//    public void setup() throws Exception {
//        String otelEndpoint = "http://localhost:" + environment.getServicePort("fleet-server_1", OTEL_PORT);
//        String esEndpoint = "http://localhost:" + environment.getServicePort("elasticsearch_1", ELASTICSEARCH_PORT);
//        String kibanaEndpoint = "http://localhost:" + environment.getServicePort("kibana_1", KIBANA_PORT);
//
//        SystemCredentialsProvider.getInstance().getCredentials().add(
//            new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, CRED_ID, "", ElasticsearchContainer.USER_NAME, ElasticsearchContainer.PASSWORD)
//        );
//
//        JenkinsOpenTelemetryPluginConfiguration config = JenkinsOpenTelemetryPluginConfiguration.get();
//        String properties = String.join(System.getProperty("line.separator"),
//            "otel.traces.exporter=otlp",
//            "otel.metrics.exporter=otlp",
//            "otel.logs.exporter=otlp"
//        );
//        config.setConfigurationProperties(properties);
//        config.setEndpoint(otelEndpoint);
//        config.setExporterIntervalMillis(10);
//        config.setServiceName("OtelJenkinsTest");
//        config.setServiceNamespace("OtelLogTest");
//        List<ObservabilityBackend> observabilityBackends = new ArrayList<>();
//        ElasticBackend esBackend = new ElasticBackend();
//        esBackend.setElasticsearchUrl(esEndpoint);
//        esBackend.setKibanaBaseUrl(kibanaEndpoint);
//        esBackend.setElasticsearchCredentialsId(CRED_ID);
//        observabilityBackends.add(esBackend);
//        config.setObservabilityBackends(observabilityBackends);
//        config.initializeOpenTelemetry();
//
//        Credentials credentials = new UsernamePasswordCredentials(ElasticsearchContainer.USER_NAME, ElasticsearchContainer.PASSWORD);
//        elasticsearchRetriever = new ElasticsearchLogStorageRetriever(esEndpoint, credentials, ObservabilityBackend.ERROR_TEMPLATE /* TODO  use a better template */,
//            OpenTelemetry.noop().getTracer("test"));
//    }


    @Test
    public void test() throws Exception {
        jenkinsRule.createSlave("remote", null, null);
        WorkflowJob p = jenkinsRule.jenkins.createProject(WorkflowJob.class, "p");
        p.getFullName();
        p.setDefinition(new CpsFlowDefinition("node('remote') {\n" + "  echo 'Hello'\n" + "}", true));
        WorkflowRun run = jenkinsRule.buildAndAssertSuccess(p);
        waitForLogs(run);
        jenkinsRule.assertLogContains("Hello", run);
    }

    private void waitForLogs(WorkflowRun run) throws InterruptedException {
        // volume of retrieved logs in bytes
        long logsLength = 0;
        MonitoringAction action = run.getAction(MonitoringAction.class);
        String traceId = action.getTraceId();
        String spanId = action.getSpanId();
        boolean complete = true;
        do {
            try {
                Instant startTime = Instant.ofEpochMilli(run.getStartTimeInMillis());
                Instant endTime = run.getDuration() == 0 ? null : startTime.plusMillis(run.getDuration());
                LogsQueryResult logsQueryResult = elasticsearchRetriever.overallLog(run.getParent().getFullName(), run.getNumber(), traceId, spanId, complete, startTime, endTime);
                logsLength = logsQueryResult.getByteBuffer().length();
            } catch (Throwable e) {
                //NOOP
            }
            Thread.sleep(1000);
        } while (logsLength < 10);
    }
}
