/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import static org.junit.Assert.assertFalse;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend.TemplateBindings;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import jenkins.model.GlobalConfiguration;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.DockerHealthcheckWaitStrategy;

/**
 * Elastic Stack containers used on the tests.
 */
public class ElasticStack extends ComposeContainer {
    public static final String SERVICE_SUFFIX = "-1";
    public static final String EDOT_SERVICE = suffixService("edot");
    public static final String KIBANA_SERVICE = suffixService("kibana");
    public static final String ELASTICSEARCH_SERVICE = suffixService("elasticsearch");
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

    private static final String suffixService(String service) {
        return service + SERVICE_SUFFIX;
    }

    public ElasticStack() {
        super(new File("src/test/resources/docker-compose.yml"));
        this.withExposedService(ELASTICSEARCH_SERVICE, ELASTICSEARCH_PORT)
            .withExposedService(KIBANA_SERVICE, KIBANA_PORT)
            .withExposedService(EDOT_SERVICE, OTEL_PORT)
            .waitingFor(EDOT_SERVICE, new DockerHealthcheckWaitStrategy())
            .withStartupTimeout(Duration.ofMinutes(10));
    }

    /**
     * @return The URL to access to the Elasticsearch Docker container.
     */
    public String getEsUrl() {
        return "http://localhost:" + ELASTICSEARCH_PORT;
    }

    /**
     * @return The URL to access to the Kibana Docker container.
     */
    public String getKibanaUrl() {
        return "http://localhost:" + KIBANA_PORT;
    }

    /**
     * @return The URL to access to the OpenTelemetry Docker container.
     */
    public String getFleetUrl() {
        return "http://localhost:" + OTEL_PORT;
    }

    /**
     * Create the index {@link #INDEX} for testing in Elasticsearch.
     *
     * @throws IOException
     */
    public void createLogIndex() throws IOException {
        try (ElasticsearchClient client = ElasticsearchClient.of(b -> b.host(getEsUrl())
                                                                       .usernameAndPassword(USER_NAME, PASSWORD))) {
            client.indices().create(c -> c.index(INDEX)
                                          .mappings(m -> m
                                              .properties("@timestamp", p -> p.date(d -> d))
                                              .properties(TemplateBindings.TRACE_ID, p -> p.keyword(k -> k))
                                              .properties(TemplateBindings.SPAN_ID, p -> p.keyword(k -> k))
                                          )
                                          .settings(settings -> settings.numberOfReplicas("0"))
            );

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

    public void configureElasticBackEnd() {
        // initialize ReconfigurableOpenTelemetry and set it as GlobalOpenTelemetry instance
        ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = ReconfigurableOpenTelemetry.get();
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(reconfigurableOpenTelemetry);

        final JenkinsOpenTelemetryPluginConfiguration configuration = GlobalConfiguration.all()
                                                                                         .get(
                                                                                             JenkinsOpenTelemetryPluginConfiguration.class);
        elasticBackendConfiguration = (ElasticBackend) configuration.getObservabilityBackends().get(0);
        elasticStackConfiguration = ((ElasticLogsBackendWithJenkinsVisualization) elasticBackendConfiguration
            .getElasticLogsBackend());

        // overrides the configuration defined in jcasc-elastic-backend.yml
        configuration.setEndpoint(getFleetUrl());
        elasticBackendConfiguration.setKibanaBaseUrl(getKibanaUrl());
        elasticBackendConfiguration.setEnableEDOT(true);
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
