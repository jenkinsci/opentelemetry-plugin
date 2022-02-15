/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import io.jenkins.plugins.opentelemetry.job.log.LogsQueryContext;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import io.opentelemetry.api.OpenTelemetry;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ElasticsearchRetrieverIT {

    @Test
    public void test() throws IOException {
        InputStream envAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(".env");
        Assert.assertNotNull(".env file not found in classpath", envAsStream);
        Properties env = new Properties();
        env.load(envAsStream);

        String url = env.getProperty("elasticsearch.url");
        String username = env.getProperty("elasticsearch.username");
        String password = env.getProperty("elasticsearch.password");
        String kibanaBaseUrl = env.getProperty("kibana.baseUrl");

        ElasticsearchLogStorageRetriever elasticsearchLogStorageRetriever = new ElasticsearchLogStorageRetriever(
            url,
            new UsernamePasswordCredentials(username, password),
            kibanaBaseUrl,
            OpenTelemetry.noop().getTracer("test"));

        Assert.assertTrue(elasticsearchLogStorageRetriever.indexTemplateExists());

        final int MAX = 10;
        int counter = 0;
        LogsQueryContext logsQueryContext = null;
        LogsQueryResult logsQueryResult;
        boolean complete = true;
        do {
            System.out.println("Request " + counter);
            logsQueryResult = elasticsearchLogStorageRetriever.overallLog("ed4e940f4a2f817e9449ed2d3d7248cb", "", complete, (ElasticsearchLogsQueryContext) logsQueryContext);
            logsQueryContext = logsQueryResult.getLogsQueryContext();
            complete = false;
            counter++;
        } while (!logsQueryResult.isComplete() && counter < MAX);
        Assert.assertTrue(counter < MAX);
    }
}
