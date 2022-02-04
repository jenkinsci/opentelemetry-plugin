/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.job.log;

import io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryTracesSemanticConventions;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.xcontent.XContentType;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.time.Duration;

import static io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryTracesSemanticConventions.SPAN_ID;
import static io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryTracesSemanticConventions.TRACE_ID;

/**
 * Elasticsearch container used on the tests.
 */
public class ElasticsearchContainer extends GenericContainer {
    public static final String INDEX_PATTERN = "logs-*";
    public static final String USER_NAME = "admin";
    public static final String PASSWORD = "changeme";
    public static final String INDEX = "logs-001";
    public static final int ES_PORT = 9200;

    public ElasticsearchContainer() {
        super("docker.elastic.co/elasticsearch/elasticsearch:7.16.3");
        withExposedPorts(ES_PORT);
        withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
        withEnv("discovery.type", "single-node");
        withEnv("bootstrap.memory_lock", "true");
        withEnv("ELASTIC_PASSWORD", PASSWORD);
        withEnv("xpack.security.enabled", "true");
        withStartupTimeout(Duration.ofMinutes(3));
    }

    /**
     * @return the RestClientBuilder to create the Elasticsearch REST client.
     */
    public RestClientBuilder getBuilder() {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        org.apache.http.auth.UsernamePasswordCredentials credentials = new org.apache.http.auth.UsernamePasswordCredentials(
            USER_NAME, PASSWORD);
        credentialsProvider.setCredentials(AuthScope.ANY, credentials);

        RestClientBuilder builder = RestClient.builder(HttpHost.create(getUrl()));
        builder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
            @Override
            public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }
        });
        return builder;
    }

    /**
     * @return The URL to access to the Elasticsearch Docker container.
     */
    public String getUrl() {
        return "http://" + this.getContainerIpAddress() + ":" + this.getMappedPort(ES_PORT);
    }

    /**
     * Create the index {@link #INDEX} fot testing in Elasticsearch.
     *
     * @throws IOException
     */
    public void createLogIndex() throws IOException {
        try (RestHighLevelClient client = new RestHighLevelClient(getBuilder())) {
            CreateIndexRequest request = new CreateIndexRequest(INDEX);
            client.indices().create(request, RequestOptions.DEFAULT);

            BulkRequest bulkRequest = new BulkRequest();
            for (int n = 0; n < 100; n++) {
                bulkRequest.add(newBulk(n, "1"));
                bulkRequest.add(newBulk(n, "2"));
                bulkRequest.add(newBulk(n, "3"));
            }
            bulkRequest.timeout(TimeValue.timeValueMinutes(2));
            bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);

            client.bulk(bulkRequest, RequestOptions.DEFAULT);
        }
    }

    private IndexRequest newBulk(int lineNumber, String buildID) throws IOException {
        return new IndexRequest(INDEX).source(XContentType.JSON, TRACE_ID, "foo", SPAN_ID, "bar");
    }
}
