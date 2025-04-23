/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import io.jenkins.plugins.opentelemetry.job.log.LogLine;
import io.opentelemetry.api.OpenTelemetry;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.protocol.HttpContext;

import org.junit.Test;


import java.io.InputStream;
import java.time.Instant;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class LokiBuildLogsLineIteratorTest {

    @Test
    public void testLoadLokiQueryResponse() {
        CloseableHttpClient httpClient = HttpClients.custom().build();

        Instant pipelineStartTime = Instant.ofEpochMilli(TimeUnit.MILLISECONDS.convert(1718111754515426000L, TimeUnit.NANOSECONDS));

        InputStream lokiLogsQueryResponseStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("io/jenkins/plugins/opentelemetry/backend/grafana/loki_query_response.json");
        assertNotNull(lokiLogsQueryResponseStream);
        LokiGetJenkinsBuildLogsQueryParameters lokiQueryParameters = new LokiGetJenkinsBuildLogsQueryParametersBuilder()
            .setJobFullName("my-war/master").setRunNumber(384)
            .setTraceId("69a627b7bc02241b6029bed20f4ff8d8")
            .setStartTime(pipelineStartTime.minusSeconds(600))
            .setEndTime(pipelineStartTime.plusSeconds(600))
            .setServiceName("jenkins")
            .setServiceNamespace("jenkins")
            .build();
        try (LokiBuildLogsLineIterator lokiBuildLogsLineIterator = new LokiBuildLogsLineIterator(
            lokiQueryParameters, httpClient,
            HttpClientContext.create(),
            "http://localhost:3100",
            Optional.of(new org.apache.hc.client5.http.auth.UsernamePasswordCredentials("admin", "changeme".toCharArray())),
            Optional.empty(),
            OpenTelemetry.noop().getTracer("io.jenkins")
        )) {
            Iterator<LogLine<Long>> logLines = lokiBuildLogsLineIterator.loadLogLines(lokiLogsQueryResponseStream);
            while (logLines.hasNext()) {
                LogLine<Long> logLine = logLines.next();
                System.out.println(logLine);
            }
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}