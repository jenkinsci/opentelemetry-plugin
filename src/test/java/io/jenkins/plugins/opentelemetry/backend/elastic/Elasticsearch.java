/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import java.io.File;
import java.time.Duration;

import org.testcontainers.containers.DockerComposeContainer;

/**
 * Elastic Stack containers used on the tests.
 */
public class Elasticsearch extends DockerComposeContainer<Elasticsearch> {
    public static final String USER_NAME = "admin";
    public static final String PASSWORD = "changeme";
    public static final String INDEX = "logs-001";
    public static final int ES_PORT = 9200;

    public static final int OTEL_PORT = 8200;
    public static final int KIBANA_PORT = 5601;
    public static final int ELASTICSEARCH_PORT = 9200;

    public static final String WRONG_CREDS = "wrongCreds";
    public static final String CRED_ID = "credID";

    public Elasticsearch() {
        super(new File("src/test/resources/docker-compose-es.yml"));
        withExposedService("elasticsearch_1", ELASTICSEARCH_PORT)
                .withStartupTimeout(Duration.ofMinutes(10));
    }

    /**
     * @return The URL to access to the Elasticsearch Docker container.
     */
    public String getEsUrl() {
        return "http://" + this.getServiceHost("elasticsearch_1", ELASTICSEARCH_PORT) + ":" + this
                .getServicePort("elasticsearch_1", ELASTICSEARCH_PORT);
    }
}
