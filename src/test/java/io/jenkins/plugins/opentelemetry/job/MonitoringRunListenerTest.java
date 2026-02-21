/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Job;
import hudson.model.Run;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Map;
import org.junit.Test;

public class MonitoringRunListenerTest {

    @Test
    public void test_default_allow_deny_list() {
        MonitoringRunListener monitoringRunListener = new MonitoringRunListener();
        Map<String, String> configProperties = Map.of();
        monitoringRunListener.afterConfiguration(DefaultConfigProperties.createFromMap(configProperties));
        String jobFullName = "my-team/my-war/main";
        assertFalse(monitoringRunListener
                .runDurationHistogramAllowList
                .matcher(jobFullName)
                .matches());
        assertFalse(monitoringRunListener
                .runDurationHistogramDenyList
                .matcher(jobFullName)
                .matches());
    }

    @Test
    public void test_deny_list_matching() {
        MonitoringRunListener monitoringRunListener = new MonitoringRunListener();
        Map<String, String> configProperties = Map.of(
                "otel.instrumentation.jenkins.run.metric.duration.allow_list",
                "my-team/.*",
                "otel.instrumentation.jenkins.run.metric.duration.deny_list",
                ".*test.*");
        monitoringRunListener.afterConfiguration(DefaultConfigProperties.createFromMap(configProperties));
        String jobFullName = "my-team/my-war/test-123";
        assertTrue(monitoringRunListener
                .runDurationHistogramAllowList
                .matcher(jobFullName)
                .matches());
        assertTrue(monitoringRunListener
                .runDurationHistogramDenyList
                .matcher(jobFullName)
                .matches());
    }

    @Test
    public void test_deny_list_not_matching() {
        MonitoringRunListener monitoringRunListener = new MonitoringRunListener();
        Map<String, String> configProperties = Map.of(
                "otel.instrumentation.jenkins.run.metric.duration.allow_list",
                "my-team/.*",
                "otel.instrumentation.jenkins.run.metric.duration.deny_list",
                ".*test.*");
        monitoringRunListener.afterConfiguration(DefaultConfigProperties.createFromMap(configProperties));
        String jobFullName = "my-team/my-war/main";
        assertTrue(monitoringRunListener
                .runDurationHistogramAllowList
                .matcher(jobFullName)
                .matches());
        assertFalse(monitoringRunListener
                .runDurationHistogramDenyList
                .matcher(jobFullName)
                .matches());
    }

    @Test
    public void test_allow_list_matching() {
        MonitoringRunListener monitoringRunListener = new MonitoringRunListener();
        Map<String, String> configProperties =
                Map.of("otel.instrumentation.jenkins.run.metric.duration.allow_list", "my-team/.*");
        monitoringRunListener.afterConfiguration(DefaultConfigProperties.createFromMap(configProperties));
        String jobFullName = "my-team/my-war/main";
        assertTrue(monitoringRunListener
                .runDurationHistogramAllowList
                .matcher(jobFullName)
                .matches());
        assertFalse(monitoringRunListener
                .runDurationHistogramDenyList
                .matcher(jobFullName)
                .matches());
    }

    @Test
    public void test_allow_list_not_matching() {
        MonitoringRunListener monitoringRunListener = new MonitoringRunListener();
        Map<String, String> configProperties =
                Map.of("otel.instrumentation.jenkins.run.metric.duration.allow_list", "my-team/.*");
        monitoringRunListener.afterConfiguration(DefaultConfigProperties.createFromMap(configProperties));
        String jobFullName = "another-team/my-war/main";
        assertFalse(monitoringRunListener
                .runDurationHistogramAllowList
                .matcher(jobFullName)
                .matches());
        assertFalse(monitoringRunListener
                .runDurationHistogramDenyList
                .matcher(jobFullName)
                .matches());
    }

    @Test
    public void testCountersIncludePipelineIdWhenAllowed() {
        MonitoringRunListener listener = new MonitoringRunListener();

        ConfigProperties config = DefaultConfigProperties.createFromMap(
                Map.of("otel.instrumentation.jenkins.run.metric.allow_list", ".*"));
        listener.afterConfiguration(config);

        Run<?, ?> run = mockRun("folder/job-name");

        assertEquals("folder/job-name", listener.getPipelineIdForCounters(run));
    }

    @Test
    public void testCountersCollapsePipelineIdWhenDenied() {
        MonitoringRunListener listener = new MonitoringRunListener();

        ConfigProperties config = DefaultConfigProperties.createFromMap(
                Map.of("otel.instrumentation.jenkins.run.metric.allow_list", "team-a/.*"));
        listener.afterConfiguration(config);

        Run<?, ?> run = mockRun("team-b/job-x");

        assertEquals("#other#", listener.getPipelineIdForCounters(run));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRegexThrowsException() {
        MonitoringRunListener listener = new MonitoringRunListener();

        ConfigProperties config = DefaultConfigProperties.createFromMap(
                Map.of("otel.instrumentation.jenkins.run.metric.allow_list", "*invalid["));

        listener.afterConfiguration(config);
    }

    @Test
    public void testDurationAllowAndDenyLists() {
        MonitoringRunListener listener = new MonitoringRunListener();

        ConfigProperties config = DefaultConfigProperties.createFromMap(Map.of(
                "otel.instrumentation.jenkins.run.metric.duration.allow_list", "my-team/.*",
                "otel.instrumentation.jenkins.run.metric.duration.deny_list", ".*test.*"));
        listener.afterConfiguration(config);

        String job = "my-team/my-war/test-123";
        assertTrue(listener.runDurationHistogramAllowList.matcher(job).matches());
        assertTrue(listener.runDurationHistogramDenyList.matcher(job).matches());
    }

    @SuppressWarnings("rawtypes")
    private Run<?, ?> mockRun(String fullName) {
        Job job = mock(Job.class);
        when(job.getFullName()).thenReturn(fullName);

        Run run = mock(Run.class);
        when(run.getParent()).thenReturn(job);

        return run;
    }

    @Test
    public void testCountersAndDurationUseIndependentAllowLists() {
        MonitoringRunListener listener = new MonitoringRunListener();

        ConfigProperties config = DefaultConfigProperties.createFromMap(Map.of(
                "otel.instrumentation.jenkins.run.metric.allow_list", "team-a/.*",
                "otel.instrumentation.jenkins.run.metric.duration.allow_list", "team-b/.*"));
        listener.afterConfiguration(config);

        Run<?, ?> runA = mockRun("team-a/job1");
        Run<?, ?> runB = mockRun("team-b/job2");

        assertEquals("team-a/job1", listener.getPipelineIdForCounters(runA));
        assertEquals("#other#", listener.getPipelineIdForCounters(runB));

        assertEquals("#other#", listener.getPipelineIdForDuration(runA));
        assertEquals("team-b/job2", listener.getPipelineIdForDuration(runB));
    }
}
