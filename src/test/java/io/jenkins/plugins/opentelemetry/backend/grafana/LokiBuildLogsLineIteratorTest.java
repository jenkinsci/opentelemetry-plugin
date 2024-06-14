/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import io.opentelemetry.api.OpenTelemetry;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
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
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();

        Instant pipelineStartTime = Instant.ofEpochMilli(TimeUnit.MILLISECONDS.convert(1718111754515426000L, TimeUnit.NANOSECONDS));

        InputStream lokiLogsQueryResponseStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("io/jenkins/plugins/opentelemetry/backend/grafana/loki_query_response.json");
        assertNotNull(lokiLogsQueryResponseStream);
        try (LokiBuildLogsLineIterator lokiBuildLogsLineIterator = new LokiBuildLogsLineIterator(
            "my-war/master",
            384,
            "69a627b7bc02241b6029bed20f4ff8d8",
            Optional.empty(),
            pipelineStartTime.minusSeconds(600),
            Optional.of(pipelineStartTime.plusSeconds(600)),
            "jenkins",
             Optional.of("jenkins"),
            httpClient,
            new BasicHttpContext(),
            "http://localhost:3100",
            Optional.of(new UsernamePasswordCredentials("jenkins", "jenkins")),
            Optional.empty(),
            OpenTelemetry.noop().getTracer("io.jenkins")
        )) {
            Iterator<String> logLines = lokiBuildLogsLineIterator.loadLogLines(lokiLogsQueryResponseStream);
            while (logLines.hasNext()) {
                String logLine = logLines.next();
                System.out.println(logLine);
            }
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}