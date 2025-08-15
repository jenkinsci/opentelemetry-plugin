/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonitoringRunListenerTest {

    @Test
    void test_default_allow_deny_list() {
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
    void test_deny_list_matching() {
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
    void test_deny_list_not_matching() {
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
    void test_allow_list_matching() {
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
    void test_allow_list_not_matching() {
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
}
