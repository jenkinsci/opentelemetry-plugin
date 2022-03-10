/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.google.common.io.ByteStreams;
import io.jenkins.plugins.opentelemetry.job.log.util.StreamingInputStream;
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
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ElasticsearchLogsSearchIteratorIT {

    @Test
    public void testElasticsearchLogsSearchIterator() throws IOException {
        ElasticsearchLogsSearchIterator elasticsearchLogsSearchIterator = getElasticsearchLogsSearchIterator();
        while (elasticsearchLogsSearchIterator.hasNext()) {
            System.out.println(elasticsearchLogsSearchIterator.next());
        }
        System.out.println("Queries count: " + elasticsearchLogsSearchIterator.queryCounter.get());
    }

    @Test
    public void testStreamingInputStream() throws IOException {
        ElasticsearchLogsSearchIterator elasticsearchLogsSearchIterator = getElasticsearchLogsSearchIterator();
        StreamingInputStream streamingInputStream = new StreamingInputStream(elasticsearchLogsSearchIterator,true, TracerProvider.noop().get("noop"));
        ByteStreams.copy(streamingInputStream, System.out);

        System.out.println("Queries count: " + elasticsearchLogsSearchIterator.queryCounter.get());
        System.out.println("context : " + elasticsearchLogsSearchIterator.context);
    }

    @NonNull
    private ElasticsearchLogsSearchIterator getElasticsearchLogsSearchIterator() throws IOException {
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


        ElasticsearchLogsSearchIterator elasticsearchLogsSearchIterator = new ElasticsearchLogsSearchIterator("my-war/master", 136, "1253b77680aa4f5a709e76381e5523f1", null, null, esClient, TracerProvider.noop().get("noop"));
        return elasticsearchLogsSearchIterator;
    }

}