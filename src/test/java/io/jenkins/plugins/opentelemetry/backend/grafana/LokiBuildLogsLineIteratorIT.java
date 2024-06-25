/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import io.jenkins.plugins.opentelemetry.job.log.LogLine;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.internal.JavaVersionSpecific;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.junit.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class LokiBuildLogsLineIteratorIT {
    @Test
    public void overallLog() throws Exception {
        System.out.println("OTel Java Specific Version: " + JavaVersionSpecific.get());

        InputStream env = Thread.currentThread().getContextClassLoader().getResourceAsStream(".env");
        Properties properties = new Properties();
        properties.load(env);
        String lokiUser = properties.getProperty("loki.user");
        String lokiPassword = properties.getProperty("loki.apiKey");
        UsernamePasswordCredentials lokiCredentials = new UsernamePasswordCredentials(lokiUser, lokiPassword);

        String lokiUrl = properties.getProperty("loki.url");

        System.out.println(lokiUrl);
        System.out.println(lokiUser);

        CloseableHttpClient httpClient = HttpClientBuilder.create().build();

        Instant pipelineStartTime = Instant.ofEpochMilli(TimeUnit.MILLISECONDS.convert(1718111754515426000L, TimeUnit.NANOSECONDS));


        LokiGetJenkinsBuildLogsQueryParameters lokiQueryParameters = new LokiGetJenkinsBuildLogsQueryParametersBuilder()
            .setJobFullName("my-war/master")
            .setRunNumber(384)
            .setTraceId("69a627b7bc02241b6029bed20f4ff8d8")
            .setStartTime(pipelineStartTime.minusSeconds(600))
            .setEndTime(pipelineStartTime.plusSeconds(600))
            .setServiceName("jenkins")
            .setServiceNamespace("jenkins")
            .build();
        try (LokiBuildLogsLineIterator lokiBuildLogsLineIterator = new LokiBuildLogsLineIterator(
            lokiQueryParameters, httpClient,
            new BasicHttpContext(),
            lokiUrl,
            Optional.of(lokiCredentials),
            Optional.empty(),
            OpenTelemetry.noop().getTracer("io.jenkins")
        )) {
            while (lokiBuildLogsLineIterator.hasNext()) {
                LogLine<Long> line = lokiBuildLogsLineIterator.next();
                System.out.println(line.getMessage());
            }
        }
    }
}