/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.google.common.io.ByteStreams;
import io.jenkins.plugins.opentelemetry.job.log.util.LineIteratorInputStream;
import io.jenkins.plugins.opentelemetry.job.log.util.LineIterator;
import io.opentelemetry.api.trace.TracerProvider;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.junit.Assert;
import org.junit.Test;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ElasticsearchBuildLogsLineIteratorIT {

    @Test
    public void testElasticsearchLogsSearchIterator() throws IOException {
        ElasticsearchBuildLogsLineIterator elasticsearchBuildLogsLineIterator = getElasticsearchLogsSearchIterator();
        int counter = 0;
        while (elasticsearchBuildLogsLineIterator.hasNext()) {
            System.out.println(elasticsearchBuildLogsLineIterator.next());
            counter++;
        }
        System.out.println("Line count: " + counter);
        System.out.println("Query count: " + elasticsearchBuildLogsLineIterator.queryCounter);
    }

    @Test
    public void testStreamingInputStream() throws IOException {
        ElasticsearchBuildLogsLineIterator elasticsearchBuildLogsLineIterator = getElasticsearchLogsSearchIterator();
        LineIterator.LineBytesToLineNumberConverter converter = new LineIterator.LineBytesToLineNumberConverter() {
            final Map<Long, Long> context = new HashMap<>();
            @Nullable
            @Override
            public Long getLogLineFromLogBytes(long bytes) {
                return context.get(bytes);
            }

            @Override
            public void putLogBytesToLogLine(long bytes, long line) {
                context.put(bytes, line);

            }
        };
        LineIteratorInputStream lineIteratorInputStream = new LineIteratorInputStream(elasticsearchBuildLogsLineIterator, converter, TracerProvider.noop().get("noop"));
        ByteStreams.copy(lineIteratorInputStream, System.out);

        System.out.println("Queries count: " + elasticsearchBuildLogsLineIterator.queryCounter);
    }

    @NonNull
    private ElasticsearchBuildLogsLineIterator getElasticsearchLogsSearchIterator() throws IOException {
        InputStream envAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(".env");
        Assert.assertNotNull(".env file not found in classpath", envAsStream);
        Properties env = new Properties();
        env.load(envAsStream);

        String elasticsearchUrl = env.getProperty("elasticsearch.url");
        String username = env.getProperty("elasticsearch.username");
        String password = env.getProperty("elasticsearch.password");

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        Credentials elasticsearchCredentials = new UsernamePasswordCredentials(username, password);
        credentialsProvider.setCredentials(AuthScope.ANY, elasticsearchCredentials);

        RestClient restClient = RestClient.builder(HttpHost.create(elasticsearchUrl))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
            .build();
        RestClientTransport elasticsearchTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        ElasticsearchClient esClient = new ElasticsearchClient(elasticsearchTransport);


        return new ElasticsearchBuildLogsLineIterator("my-war/master", 136, "1253b77680aa4f5a709e76381e5523f1", null, esClient, TracerProvider.noop().get("noop"));
    }

}