/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;

public class PipelineElasticsearchBackendTest extends ElasticStackIT {

    private ElasticsearchLogStorageRetriever elasticsearchRetriever;

    @Rule
    public static Timeout globalTimeout = Timeout.builder().withTimeout(10, TimeUnit.MINUTES).withLookingForStuckThread(true).build();
    
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
        LogsQueryResult logsQueryResult = null;
        ByteBuffer byteBuffer = null;
        do {
            try {
                Instant startTime = Instant.ofEpochMilli(run.getStartTimeInMillis());
                Instant endTime = run.getDuration() == 0 ? null : startTime.plusMillis(run.getDuration());
                logsQueryResult = elasticsearchRetriever.overallLog(run.getParent().getFullName(),
                        run.getNumber(), traceId, spanId, complete, startTime, endTime);
                byteBuffer = logsQueryResult.getByteBuffer();
                logsLength = byteBuffer.length();
            } catch (Throwable e) {
                // NOOP
            }
            Thread.sleep(1000);
        } while (logsLength < 10);
        assertNotNull(byteBuffer);
        String logContent = byteBuffer.toString();
        assertTrue(logContent.contains("Hello"));
    }
}
