/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import static org.junit.Assume.assumeTrue;

import java.time.Instant;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.util.concurrent.TimeUnit;
import org.junit.rules.Timeout;

public class PipelineElasticsearchBackendTest {

    public static final String CRED_ID = "credID";
    @ClassRule
    @ConfiguredWithCode("/io/jenkins/plugins/opentelemetry/jcasc-elastic-backend.yml")
    public static JenkinsConfiguredWithCodeRule jenkinsRule = new JenkinsConfiguredWithCodeRule();
    @ClassRule
    public static ElasticStack elasticStack = new ElasticStack();
    private ElasticsearchLogStorageRetriever elasticsearchRetriever;

    @Rule
    public Timeout globalTimeout = new Timeout(10, TimeUnit.MINUTES);

    @BeforeClass
    public static void requiresDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }

    @BeforeClass
    public static void beforeClass() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterClass
    public static void afterClass() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Before
    public void setUp() throws Exception {
        elasticStack.configureElasticBackEnd();
    }

    @Test
    public void test() throws Exception {
        jenkinsRule.createSlave("remote", null, null);
        WorkflowJob p = jenkinsRule.jenkins.createProject(WorkflowJob.class, "p");
        p.getFullName();
        p.setDefinition(new CpsFlowDefinition("node('remote') {\n" + "  echo 'Hello'\n" + "}", true));
        WorkflowRun run = jenkinsRule.buildAndAssertSuccess(p);
        waitForLogs(run);
        jenkinsRule.assertLogContains("Hello", run);
        // Aseert that logs are stored in Elastic Stack
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
                LogsQueryResult logsQueryResult = elasticsearchRetriever.overallLog(run.getParent().getFullName(),
                        run.getNumber(), traceId, spanId, complete, startTime, endTime);
                logsLength = logsQueryResult.getByteBuffer().length();
            } catch (Throwable e) {
                // NOOP
            }
            Thread.sleep(1000);
        } while (logsLength < 10);
    }
}
