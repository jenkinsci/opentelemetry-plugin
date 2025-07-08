/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.jvnet.hudson.test.TestExtension;
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
    public static ElasticStack elasticStack = new ElasticStack();

    @BeforeEach
    void beforeEach() {
        elasticStack.getServicePort(ElasticStack.EDOT_SERVICE, ElasticStack.OTEL_PORT);
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

    @TestExtension
    public static class IndexInitializer {
        /**
         * Initializes the log index in the Elastic Stack.
         * Declared as an initializer to ensure it runs after {@link io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry#init()} and avoids exception by {@link io.opentelemetry.api.GlobalOpenTelemetry#set(io.opentelemetry.api.OpenTelemetry)}
         */
        @Initializer(after = InitMilestone.SYSTEM_CONFIG_LOADED)
        public void init() throws Exception {
            elasticStack.createLogIndex();
        }
    }
}
