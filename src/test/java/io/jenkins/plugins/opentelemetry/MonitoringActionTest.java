/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import groovy.text.GStringTemplateEngine;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class MonitoringActionTest {

    @Test
    public void testGenerateVisualisationUrl() throws IOException, ClassNotFoundException {
        String template = "${baseUrl}/app/apm/services/${serviceName}/transactions/view" +
                "?rangeFrom=${startTime.minusSeconds(600)}" +
                "&rangeTo=${startTime.plusSeconds(600)}" +
                "&transactionName=${rootSpanName}" +
                "&transactionType=unknown" +
                "&latencyAggregationType=avg" +
                "&traceId=${traceId}" +
                "&transactionId=${spanId}";

        Map<String, Object> binding = new HashMap<>();
        binding.put("baseUrl", "https://localhost:9200");
        binding.put("serviceName", JenkinsOtelSemanticAttributes.JENKINS);
        binding.put("rootSpanName", "my-pipeline");
        binding.put("traceId", "ef7e4138d38d9e24c494ce123ccbad5d");
        binding.put("spanId", "a3bab980d6a51ba9");
        binding.put("startTime", Instant.ofEpochMilli(1613086645141L));
        String actual = new GStringTemplateEngine().createTemplate(template).make(binding).toString();
        System.out.println(actual);
    }

    @BeforeClass
    public static void beforeClass() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterClass
    public static void afterClass() {
        GlobalOpenTelemetry.resetForTest();
    }
}
