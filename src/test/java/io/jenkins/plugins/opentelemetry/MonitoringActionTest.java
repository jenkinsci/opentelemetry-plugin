/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import groovy.text.GStringTemplateEngine;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MonitoringActionTest {

    @Test
    public void testGenerateVisualisationUrl() throws IOException, ClassNotFoundException {
        String template = "${baseUrl}/app/apm/services/${serviceName}/transactions/view"
                + "?rangeFrom=${startTime.minusSeconds(600)}"
                + "&rangeTo=${startTime.plusSeconds(600)}"
                + "&transactionName=${rootSpanName}"
                + "&transactionType=unknown"
                + "&latencyAggregationType=avg"
                + "&traceId=${traceId}"
                + "&transactionId=${spanId}";

        Map<String, Object> binding = new HashMap<>();
        binding.put("baseUrl", "https://localhost:9200");
        binding.put("serviceName", ExtendedJenkinsAttributes.JENKINS);
        binding.put("rootSpanName", OtelUtils.urlEncode("my-pipeline"));
        binding.put("traceId", "ef7e4138d38d9e24c494ce123ccbad5d");
        binding.put("spanId", "a3bab980d6a51ba9");
        binding.put("startTime", Instant.ofEpochMilli(1613086645141L));
        String actual = new GStringTemplateEngine()
                .createTemplate(template)
                .make(binding)
                .toString();
        System.out.println(actual);
    }

    @Test
    public void testGenerateVisualisationUrlEncoded() throws IOException, ClassNotFoundException {
        String template = "${baseUrl}/app/apm/services/${serviceName}/transactions/view"
                + "?rangeFrom=${startTime.minusSeconds(600)}"
                + "&rangeTo=${startTime.plusSeconds(600)}"
                + "&transactionName=${rootSpanName}"
                + "&transactionType=unknown"
                + "&latencyAggregationType=avg"
                + "&traceId=${traceId}"
                + "&transactionId=${spanId}";

        Map<String, Object> binding = new HashMap<>();
        binding.put("baseUrl", "https://localhost:9200");
        binding.put("serviceName", ExtendedJenkinsAttributes.JENKINS);
        binding.put("rootSpanName", OtelUtils.urlEncode("my+job-with+chars+that+need:escaping"));
        binding.put("traceId", "ef7e4138d38d9e24c494ce123ccbad5d");
        binding.put("spanId", "a3bab980d6a51ba9");
        binding.put("startTime", Instant.ofEpochMilli(1613086645141L));
        String actual = new GStringTemplateEngine()
                .createTemplate(template)
                .make(binding)
                .toString();
        System.out.println(actual);
    }

    @BeforeAll
    public static void beforeClass() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterAll
    public static void afterClass() {
        GlobalOpenTelemetry.resetForTest();
    }
}
