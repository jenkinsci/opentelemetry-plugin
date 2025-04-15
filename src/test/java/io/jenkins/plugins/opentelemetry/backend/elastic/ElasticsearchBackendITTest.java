/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Ignore;
import org.junit.Test;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;

@Ignore("disabled until we update to EDOT")
public class ElasticsearchBackendITTest extends ElasticStackIT {

    @Test
    public void test() throws Exception {
        jenkinsRule.createOnlineSlave(new LabelAtom("remote"));
        WorkflowJob p = jenkinsRule.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node('remote') {\n" + "  echo 'Hello'\n" + "}", true));
        WorkflowRun run = jenkinsRule.buildAndAssertSuccess(p);
        jenkinsRule.waitForCompletion(run);
        waitForLogs(run);
        jenkinsRule.assertLogContains("Hello", run);
    }

    @Test
    public void testCredentialsDoValidate() {
        ElasticBackend backend = elasticStack.getElasticBackendConfiguration();
        ElasticBackend.DescriptorImpl descriptor = (ElasticBackend.DescriptorImpl) backend.getDescriptor();
        ElasticLogsBackendWithJenkinsVisualization visualization = elasticStack.getElasticStackConfiguration();
        ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl visDescriptor = (ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl) visualization
                .getDescriptor();
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckKibanaBaseUrl("http://kibana.example.com").kind);
        assertEquals(FormValidation.Kind.OK,
                visDescriptor.doValidate(elasticStack.getEsUrl(), true, ElasticStack.CRED_ID).kind);
        assertEquals(FormValidation.Kind.ERROR,
                visDescriptor.doValidate(elasticStack.getEsUrl(), true, ElasticStack.WRONG_CREDS).kind);
        assertEquals(FormValidation.Kind.ERROR, visDescriptor.doValidate("nowhere", true, ElasticStack.CRED_ID).kind);
    }

    @Test
    public void testDoFillCredentialsIdItems() {
        ElasticLogsBackendWithJenkinsVisualization visualization = elasticStack.getElasticStackConfiguration();
        ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl visDescriptor = (ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl) visualization
                .getDescriptor();
        assertFalse(visDescriptor.doFillElasticsearchCredentialsIdItems(null, ElasticStack.CRED_ID).isEmpty());
    }

    @Test
    public void testDoCheckCredentialsId() {
        ElasticLogsBackendWithJenkinsVisualization visualization = elasticStack.getElasticStackConfiguration();
        ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl visDescriptor = (ElasticLogsBackendWithJenkinsVisualization.DescriptorImpl) visualization
                .getDescriptor();
        assertEquals(FormValidation.Kind.OK,
                visDescriptor.doCheckElasticsearchCredentialsId(null, ElasticStack.CRED_ID).kind);
        assertEquals(FormValidation.Kind.ERROR, visDescriptor.doCheckElasticsearchCredentialsId(null, "foo").kind);
    }

    private void waitForLogs(WorkflowRun run) throws InterruptedException {
        LogStorageRetriever elasticsearchRetriever = elasticStack.getElasticsearchRetriever();
        MonitoringAction action = run.getAction(MonitoringAction.class);
        String traceId = action.getTraceId();
        String spanId = action.getSpanId();
        String logContent = "";
        Instant startTime = Instant.ofEpochMilli(run.getStartTimeInMillis());
        do {
            try {
                LogsQueryResult logsQueryResult = elasticsearchRetriever.overallLog(run.getParent().getFullName(),
                        run.getNumber(), traceId, spanId, true, startTime, Instant.now());
                ByteBuffer byteBuffer = logsQueryResult.getByteBuffer();
                if (byteBuffer.length() > 0) {
                    logContent = IOUtils.toString(byteBuffer.newInputStream(), StandardCharsets.UTF_8);
                    System.err.println(logContent);
                } else {
                    System.err.println("No logs yet");
                }
            } catch (Throwable e) {
                // NOOP
                System.err.println("Error while retrieving logs: " + e.getMessage());
            }
            Thread.sleep(10000);
        } while (!logContent.contains("Finished: SUCCESS"));
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
