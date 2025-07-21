/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.apache.commons.lang3.SystemUtils;

/**
 * Base class for integration tests using an Elastic Stack.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class ElasticStackIT {
    @Container
    protected static final ElasticStack elasticStack = new ElasticStack();

    @BeforeEach
    void beforeEach() throws Exception {
        elasticStack.getServicePort(ElasticStack.EDOT_SERVICE, ElasticStack.OTEL_PORT);
        elasticStack.createLogIndexIfNeeded();
    }

    @BeforeAll
    static void beforeAll() {
        assumeTrue(SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_LINUX);
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }

    @AfterAll
    static void afterAll() {
        GlobalOpenTelemetry.resetForTest();
    }
}
