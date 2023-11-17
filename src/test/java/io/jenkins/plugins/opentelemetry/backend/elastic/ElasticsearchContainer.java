/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend.TemplateBindings;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.testcontainers.containers.DockerComposeContainer;

import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;

import static junit.framework.TestCase.assertFalse;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Elasticsearch container used on the tests.
 */
public class ElasticsearchContainer extends DockerComposeContainer<ElasticsearchContainer> {
    public static final String USER_NAME = "admin";
    public static final String PASSWORD = "changeme";
    public static final String INDEX = "logs-001";
    public static final int ES_PORT = 9200;

    public static final int OTEL_PORT = 8200;
    public static final int KIBANA_PORT = 5601;
    public static final int ELASTICSEARCH_PORT = 9200;

    public ElasticsearchContainer() {
        super(new File("src/test/resources/docker-compose.yml"));
        withExposedService("fleet-server_1", OTEL_PORT)
                .withExposedService("kibana_1", KIBANA_PORT)
                .withExposedService("elasticsearch_1", ELASTICSEARCH_PORT);
    }

    /**
     * @return the RestClientBuilder to create the Elasticsearch REST client.
     */
    public RestClientBuilder getBuilder() {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        org.apache.http.auth.UsernamePasswordCredentials credentials = new org.apache.http.auth.UsernamePasswordCredentials(
                USER_NAME, PASSWORD);
        credentialsProvider.setCredentials(AuthScope.ANY, credentials);

        RestClientBuilder builder = RestClient.builder(HttpHost.create(getEsUrl()));
        builder.setHttpClientConfigCallback(
                httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        return builder;
    }

    /**
     * @return The URL to access to the Elasticsearch Docker container.
     */
    public String getEsUrl() {
        return "http://" + this.getServiceHost("elasticsearch_1", ELASTICSEARCH_PORT);
    }

    /**
     * @return The URL to access to the Kibana Docker container.
     */
    public String getKibanaUrl() {
        return "http://" + this.getServiceHost("kibana_1", KIBANA_PORT);
    }

    /**
     * @return The URL to access to the OpenTelemetry Docker container.
     */
    public String getFleetUrl() {
        return "http://" + this.getServiceHost("fleet-server_1", OTEL_PORT);
    }

    /**
     * Create the index {@link #INDEX} fot testing in Elasticsearch.
     *
     * @throws IOException
     */
    public void createLogIndex() throws IOException {
        RestClient restClient = getBuilder().build();
        RestClientTransport elasticsearchTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        ElasticsearchClient client = new ElasticsearchClient(elasticsearchTransport);

        client.indices().create(new CreateIndexRequest.Builder().index(INDEX).build());

        BulkRequest.Builder br = new BulkRequest.Builder();
        for (int n = 0; n < 100; n++) {
            String index = String.valueOf(n);
            br.operations(op -> op
                    .index(idx -> idx
                            .index(INDEX)
                            .document(
                                    Map.of(
                                            TemplateBindings.TRACE_ID, "foo" + index,
                                            TemplateBindings.SPAN_ID, "bar" + index))));
        }
        BulkResponse result = client.bulk(br.build());
        assertFalse(result.errors());
    }
}
