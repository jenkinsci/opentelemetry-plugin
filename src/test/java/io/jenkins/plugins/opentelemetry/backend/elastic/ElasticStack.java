/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.io.File;
import java.time.Duration;
import jenkins.model.GlobalConfiguration;
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
