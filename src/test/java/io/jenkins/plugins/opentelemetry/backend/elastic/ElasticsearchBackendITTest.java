/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kohsuke.stapler.framework.io.ByteBuffer;

@Timeout(value = 10, unit = TimeUnit.MINUTES)
@WithJenkinsConfiguredWithCode
public class ElasticsearchBackendITTest extends ElasticStackIT {

    private static final Logger LOGGER = Logger.getLogger(ElasticsearchBackendITTest.class.getName());

    @Test
    @ConfiguredWithCode("jcasc-elastic-backend.yml")
    public void test(JenkinsConfiguredWithCodeRule j) throws Exception {
        elasticStack.configureElasticBackEnd();
        j.createOnlineSlave(new LabelAtom("remote"));
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                node('remote') {
                    echo 'Hello'
                }""", true));
        WorkflowRun run = j.buildAndAssertSuccess(p);
        waitForLogs(run);
        j.assertLogContains("Hello", run);
    }

    @Test
    @ConfiguredWithCode("jcasc-elastic-backend.yml")
    public void testCredentialsDoValidate(JenkinsConfiguredWithCodeRule j) {
        elasticStack.configureElasticBackEnd();
        ElasticBackend backend = elasticStack.getElasticBackendConfiguration();
        ElasticBackend.DescriptorImpl descriptor = (ElasticBackend.DescriptorImpl) backend.getDescriptor();
        ElasticLogsBackendWithJenkinsVisualization visualization = elasticStack.getElasticStackConfiguration();
        ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl visDescriptor =
                (ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl) visualization.getDescriptor();

        FormValidation validation;

        validation = descriptor.doCheckKibanaBaseUrl("http://kibana.example.com");
        assertEquals(
                FormValidation.Kind.OK,
                validation.kind,
                "Kibana URL should be valid, but got: " + validation.getMessage());

        validation = visDescriptor.doValidate(elasticStack.getEsUrl(), true, ElasticStack.CRED_ID);
        assertEquals(
                FormValidation.Kind.OK,
                validation.kind,
                "Elasticsearch URL should be valid and the credentials valid " + elasticStack.getEsUrl()
                        + ", but got: "
                        + validation.renderHtml());
        validation = visDescriptor.doValidate(elasticStack.getEsUrl(), true, ElasticStack.WRONG_CREDS);
        assertEquals(
                FormValidation.Kind.ERROR,
                validation.kind,
                "Elasticsearch URL should be valid and the credentials invalid, but got: " + validation.renderHtml());
        validation = visDescriptor.doValidate("nowhere", true, ElasticStack.CRED_ID);
        assertEquals(
                FormValidation.Kind.ERROR,
                validation.kind,
                "Elasticsearch URL should be invalid, but got: " + validation.renderHtml());
    }

    @Test
    @ConfiguredWithCode("jcasc-elastic-backend.yml")
    public void testDoFillCredentialsIdItems(JenkinsConfiguredWithCodeRule j) {
        elasticStack.configureElasticBackEnd();
        ElasticLogsBackendWithJenkinsVisualization visualization = elasticStack.getElasticStackConfiguration();
        ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl visDescriptor =
                (ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl) visualization.getDescriptor();
        assertFalse(visDescriptor
                .doFillElasticsearchCredentialsIdItems(null, ElasticStack.CRED_ID)
                .isEmpty());
    }

    @Test
    @ConfiguredWithCode("jcasc-elastic-backend.yml")
    public void testDoCheckCredentialsId(JenkinsConfiguredWithCodeRule j) {
        elasticStack.configureElasticBackEnd();
        ElasticLogsBackendWithJenkinsVisualization visualization = elasticStack.getElasticStackConfiguration();
        ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl visDescriptor =
                (ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl) visualization.getDescriptor();
        assertEquals(
                FormValidation.Kind.OK,
                visDescriptor.doCheckElasticsearchCredentialsId(null, ElasticStack.CRED_ID).kind);
        assertEquals(FormValidation.Kind.ERROR, visDescriptor.doCheckElasticsearchCredentialsId(null, "foo").kind);
    }

    private void waitForLogs(WorkflowRun run) throws InterruptedException {
        LogStorageRetriever elasticsearchRetriever = elasticStack.getElasticsearchRetriever();
        MonitoringAction action = run.getAction(MonitoringAction.class);
        String traceId = action.getTraceId();
        String spanId = action.getSpanId();
        AtomicReference<String> logContentReference = new AtomicReference<>();
        Instant startTime = Instant.ofEpochMilli(run.getStartTimeInMillis());

        await().atMost(1, TimeUnit.MINUTES).pollInterval(1, TimeUnit.SECONDS).until(() -> {
            try {
                LogsQueryResult logsQueryResult = elasticsearchRetriever.overallLog(
                        run.getParent().getFullName(),
                        run.getNumber(),
                        traceId,
                        spanId,
                        true,
                        startTime,
                        Instant.now());
                ByteBuffer byteBuffer = logsQueryResult.getByteBuffer();
                if (byteBuffer.length() > 0) {
                    String logContent = IOUtils.toString(byteBuffer.newInputStream(), StandardCharsets.UTF_8);
                    logContentReference.set(logContent);
                    return logContent.contains("Finished: SUCCESS");
                } else {
                    LOGGER.info("No logs yet");
                    return false;
                }
            } catch (Throwable e) {
                // NOOP
                LOGGER.error("Error while retrieving logs: " + e.getMessage());
                return false;
            }
        });
        String logContent = logContentReference.get();
        assertTrue(logContent.contains("Started"));
        assertTrue(logContent.contains("[Pipeline] Start of Pipeline"));
        assertTrue(logContent.contains("[Pipeline] node"));
        assertTrue(logContent.contains("Running on"));
        assertTrue(logContent.contains("[Pipeline] {"));
        assertTrue(logContent.contains("[Pipeline] echo"));
        assertTrue(logContent.contains("Hello"));
        assertTrue(logContent.contains("[Pipeline] }"));
        assertTrue(logContent.contains("Finished: SUCCESS"));
    }
}
