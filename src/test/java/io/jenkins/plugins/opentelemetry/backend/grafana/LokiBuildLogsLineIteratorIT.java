/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

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
            lokiUrl,
            Optional.of(lokiCredentials),
            Optional.empty(),
            OpenTelemetry.noop().getTracer("io.jenkins")
        )) {
            while (lokiBuildLogsLineIterator.hasNext()) {
                String line = lokiBuildLogsLineIterator.next();
                System.out.println(line);
            }
        }
    }
}