/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class OpenTelemetryConfigurationTest {

    @Test
    public void testSingleOpenTelemetryExporters() {
        String expectedTracesExporter = "otlp";
        String expectedMetricsExporter = "otlp";
        String expectedLogsExporter = null;
        // OTLP endpoint configured through the GUI
        String otlpEndpoint = "http://localhost:4317";

        Map<String, String> configurationProperties = new HashMap<>();
        testOpenTelemetryExportersConfiguration(
                expectedTracesExporter,
                expectedMetricsExporter,
                expectedLogsExporter,
                otlpEndpoint,
                configurationProperties);
    }

    @Test
    public void testMultipleOpenTelemetryExporters_1() {
        String expectedTracesExporter = "otlp";
        String expectedMetricsExporter = "otlp,prometheus";
        String expectedLogsExporter = null;
        // OTLP endpoint configured through the GUI
        String otlpEndpoint = "http://localhost:4317";

        Map<String, String> configurationProperties =
                Collections.singletonMap("otel.metrics.exporter", "otlp,prometheus");
        testOpenTelemetryExportersConfiguration(
                expectedTracesExporter,
                expectedMetricsExporter,
                expectedLogsExporter,
                otlpEndpoint,
                configurationProperties);
    }

    @Test
    public void testMultipleOpenTelemetryExporters_2() {
        String expectedTracesExporter = "otlp";
        String expectedMetricsExporter = "prometheus,otlp";
        String expectedLogsExporter = null;
        // OTLP endpoint configured through the GUI
        String otlpEndpoint = "http://localhost:4317";

        Map<String, String> configurationProperties = Collections.singletonMap("otel.metrics.exporter", "prometheus");
        testOpenTelemetryExportersConfiguration(
                expectedTracesExporter,
                expectedMetricsExporter,
                expectedLogsExporter,
                otlpEndpoint,
                configurationProperties);
    }

    @Test
    public void testMultipleOpenTelemetryExporters_3() {
        String expectedTracesExporter = "otlp";
        String expectedMetricsExporter = "none";
        String expectedLogsExporter = null;
        // OTLP endpoint configured through the GUI
        String otlpEndpoint = "http://localhost:4317";

        Map<String, String> configurationProperties = Collections.singletonMap("otel.metrics.exporter", "none");
        testOpenTelemetryExportersConfiguration(
                expectedTracesExporter,
                expectedMetricsExporter,
                expectedLogsExporter,
                otlpEndpoint,
                configurationProperties);
    }

    @Test
    public void testMultipleOpenTelemetryExporters_4() {
        String expectedTracesExporter = "none";
        String expectedMetricsExporter = "otlp";
        String expectedLogsExporter = null;
        // OTLP endpoint configured through the GUI
        String otlpEndpoint = "http://localhost:4317";

        Map<String, String> configurationProperties = Collections.singletonMap("otel.traces.exporter", "none");
        testOpenTelemetryExportersConfiguration(
                expectedTracesExporter,
                expectedMetricsExporter,
                expectedLogsExporter,
                otlpEndpoint,
                configurationProperties);
    }

    @Test
    public void testMultipleOpenTelemetryExporters_5() {
        String expectedTracesExporter = "otlp";
        String expectedMetricsExporter = "otlp";
        String expectedLogsExporter = "none";
        // OTLP endpoint configured through the GUI
        String otlpEndpoint = "http://localhost:4317";

        Map<String, String> configurationProperties = Collections.singletonMap("otel.logs.exporter", "none");
        testOpenTelemetryExportersConfiguration(
                expectedTracesExporter,
                expectedMetricsExporter,
                expectedLogsExporter,
                otlpEndpoint,
                configurationProperties);
    }

    private static void testOpenTelemetryExportersConfiguration(
            String expectedTracesExporter,
            String expectedMetricsExporter,
            String expectedLogsExporter,
            String otlpEndpoint,
            Map<String, String> configurationProperties) {
        OpenTelemetryConfiguration configuration = new OpenTelemetryConfiguration(
                Optional.ofNullable(otlpEndpoint),
                Optional.empty(),
                Optional.empty(),
                Optional.of("my-jenkins"),
                Optional.empty(),
                Optional.empty(),
                configurationProperties);

        Map<String, String> actualOtelProperties = configuration.toOpenTelemetryProperties();
        String actualTracesExporter = actualOtelProperties.get("otel.traces.exporter");
        String actualMetricsExporter = actualOtelProperties.get("otel.metrics.exporter");
        String actualLogsExporter = actualOtelProperties.get("otel.logs.exporter");

        MatcherAssert.assertThat(actualTracesExporter, Matchers.is(expectedTracesExporter));
        MatcherAssert.assertThat(actualMetricsExporter, Matchers.is(expectedMetricsExporter));
        MatcherAssert.assertThat(actualLogsExporter, Matchers.is(expectedLogsExporter));
    }
}
