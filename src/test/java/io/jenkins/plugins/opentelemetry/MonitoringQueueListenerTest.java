/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import io.jenkins.plugins.opentelemetry.semconv.JenkinsMetrics;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporterUtils;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class MonitoringQueueListenerTest extends BaseIntegrationTest {

    @Test
    public void testQueueMetricsAreRegisteredAndExported() throws Exception {
        // Use a Pipeline job — the primary supported job type for this plugin
        WorkflowJob project = jenkinsRule.createProject(WorkflowJob.class, "queue-metrics-test");
        project.setDefinition(new CpsFlowDefinition("node() { echo 'queue-metrics-test' }", true));
        jenkinsRule.buildAndAssertSuccess(project);
        jenkinsRule.waitUntilNoActivity();

        // Force flush using the same pattern as BaseIntegrationTest line 246
        jenkinsControllerOpenTelemetry
                .getOpenTelemetrySdk()
                .getSdkMeterProvider()
                .forceFlush()
                .join(10, TimeUnit.SECONDS);

        // Get exported metrics using the same pattern as JenkinsOpenTelemetryPluginConfigurationIntegrationTest
        Map<String, MetricData> exportedMetrics = InMemoryMetricExporterUtils.getLastExportedMetricByMetricName(
                InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE.getFinishedMetricItems());

        // Assert jenkins.queue.count gauge is registered
        assertThat(
                "jenkins.queue.count metric must be registered and exported",
                exportedMetrics.get(JenkinsMetrics.JENKINS_QUEUE_COUNT),
                notNullValue());

        // Assert jenkins.queue.waiting gauge is registered
        assertThat(
                "jenkins.queue.waiting metric must be registered and exported",
                exportedMetrics.get(JenkinsMetrics.JENKINS_QUEUE_WAITING),
                notNullValue());

        // Assert jenkins.queue.blocked gauge is registered
        assertThat(
                "jenkins.queue.blocked metric must be registered and exported",
                exportedMetrics.get(JenkinsMetrics.JENKINS_QUEUE_BLOCKED),
                notNullValue());

        // Assert jenkins.queue.buildable gauge is registered
        assertThat(
                "jenkins.queue.buildable metric must be registered and exported",
                exportedMetrics.get(JenkinsMetrics.JENKINS_QUEUE_BUILDABLE),
                notNullValue());

        // Assert jenkins.queue.left counter is registered and incremented
        MetricData queueLeft = exportedMetrics.get(JenkinsMetrics.JENKINS_QUEUE_LEFT);
        assertThat("jenkins.queue.left counter must be registered and exported", queueLeft, notNullValue());
        long leftCount = queueLeft.getLongSumData().getPoints().stream()
                .mapToLong(p -> p.getValue())
                .sum();
        assertThat("At least one item must have left the queue", leftCount, greaterThanOrEqualTo(1L));

        // Assert jenkins.queue.time_spent_millis counter is registered
        assertThat(
                "jenkins.queue.time_spent_millis counter must be registered and exported",
                exportedMetrics.get(JenkinsMetrics.JENKINS_QUEUE_TIME_SPENT_MILLIS),
                notNullValue());
    }
}
