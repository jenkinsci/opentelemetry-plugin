/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import static org.junit.jupiter.api.Assertions.assertFalse;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend.TemplateBindings;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import jenkins.model.GlobalConfiguration;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.DockerHealthcheckWaitStrategy;

/**
 * Elastic Stack containers used on the tests.
 */
public class ElasticStack extends DockerComposeContainer<ElasticStack> {
    public static final String EDOT_SERVICE = "edot_1";
    public static final String KIBANA_SERVICE = "kibana_1";
    public static final String ELASTICSEARCH_SERVICE = "elasticsearch_1";
    public static final String USER_NAME = "admin";
    public static final String PASSWORD = "changeme";
    public static final String INDEX = "logs-001";

    public static final int OTEL_PORT = 4317;
    public static final int KIBANA_PORT = 5601;
    public static final int ELASTICSEARCH_PORT = 9200;

    public static final String WRONG_CREDS = "wrongCreds";
    public static final String CRED_ID = "credID";

    private ElasticLogsBackendWithJenkinsVisualization elasticStackConfiguration;
    private ElasticBackend elasticBackendConfiguration;
    private LogStorageRetriever elasticsearchRetriever;

    public ElasticStack() {
        super(new File("src/test/resources/docker-compose.yml"));
        withExposedService(ELASTICSEARCH_SERVICE, ELASTICSEARCH_PORT)
                .withExposedService(KIBANA_SERVICE, KIBANA_PORT)
                .withExposedService(EDOT_SERVICE, OTEL_PORT)
                .waitingFor(EDOT_SERVICE, new DockerHealthcheckWaitStrategy())
                .withStartupTimeout(Duration.ofMinutes(10));
    }

    /**
     * @return the RestClientBuilder to create the Elasticsearch REST client.
     */
    public Rest5ClientBuilder getBuilder() {
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(USER_NAME, PASSWORD.toCharArray());

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        try {
            credentialsProvider.setCredentials(new AuthScope(HttpHost.create(getEsUrl())), credentials);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .build();

        Rest5ClientBuilder builder = Rest5Client.builder(URI.create(getEsUrl())).setHttpClient(httpclient);
        return builder;
    }

    /**
     * @return The URL to access to the Elasticsearch Docker container.
     */
    public String getEsUrl() {
        return "http://" + this.getServiceHost(ELASTICSEARCH_SERVICE, ELASTICSEARCH_PORT) + ":"
                + this.getServicePort(ELASTICSEARCH_SERVICE, ELASTICSEARCH_PORT);
    }

    /**
     * @return The URL to access to the Kibana Docker container.
     */
    public String getKibanaUrl() {
        return "http://" + this.getServiceHost(KIBANA_SERVICE, KIBANA_PORT) + ":"
                + this.getServicePort(KIBANA_SERVICE, KIBANA_PORT);
    }

    /**
     * @return The URL to access to the OpenTelemetry Docker container.
     */
    public String getFleetUrl() {
        return "http://" + this.getServiceHost(EDOT_SERVICE, OTEL_PORT) + ":"
                + this.getServicePort(EDOT_SERVICE, OTEL_PORT);
    }

    /**
     * Create the index {@link #INDEX} fot testing in Elasticsearch.
     *
     * @throws IOException
     */
    public void createLogIndex() throws IOException {
        Rest5Client restClient = getBuilder().build();
        Rest5ClientTransport elasticsearchTransport = new Rest5ClientTransport(restClient, new JacksonJsonpMapper());
        try (ElasticsearchClient client = new ElasticsearchClient(elasticsearchTransport)) {
            client.indices()
                    .create(new CreateIndexRequest.Builder().index(INDEX).build());

            BulkRequest.Builder br = new BulkRequest.Builder();
            for (int n = 0; n < 100; n++) {
                String index = String.valueOf(n);
                br.operations(op -> op.index(idx -> idx.index(INDEX)
                        .document(Map.of(
                                TemplateBindings.TRACE_ID, "foo" + index,
                                TemplateBindings.SPAN_ID, "bar" + index))));
            }
            BulkResponse result = client.bulk(br.build());
            assertFalse(result.errors());
        }
    }

    public void configureElasticBackEnd() {
        // initialize ReconfigurableOpenTelemetry and set it as GlobalOpenTelemetry instance
        ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = ReconfigurableOpenTelemetry.get();
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(reconfigurableOpenTelemetry);

        final JenkinsOpenTelemetryPluginConfiguration configuration =
                GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class);
        elasticBackendConfiguration =
                (ElasticBackend) configuration.getObservabilityBackends().get(0);
        elasticStackConfiguration =
                ((ElasticLogsBackendWithJenkinsVisualization) elasticBackendConfiguration.getElasticLogsBackend());

        configuration.setEndpoint(getFleetUrl());
        elasticBackendConfiguration.setKibanaBaseUrl(getKibanaUrl());
        elasticStackConfiguration.setElasticsearchUrl(getEsUrl());
        // FIXME the configuration is not applied if you not save the configuration
        configuration.configureOpenTelemetrySdk();
        elasticsearchRetriever = configuration.getLogStorageRetriever();
    }

    public ElasticLogsBackendWithJenkinsVisualization getElasticStackConfiguration() {
        return elasticStackConfiguration;
    }

    public ElasticBackend getElasticBackendConfiguration() {
        return elasticBackendConfiguration;
    }

    public LogStorageRetriever getElasticsearchRetriever() {
        return elasticsearchRetriever;
    }
}
