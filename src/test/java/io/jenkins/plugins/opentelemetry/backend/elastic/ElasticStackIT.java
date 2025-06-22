/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.apache.commons.lang3.SystemUtils;

/**
 * Base class for integration tests using an Elastic Stack.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
@WithJenkins
@Testcontainers(disabledWithoutDocker = true)
public abstract class ElasticStackIT {
    @Container
    public static ElasticStack elasticStack = new ElasticStack();

    protected JenkinsRule j;

    @BeforeEach
    void beforeEach(JenkinsRule j) {
        this.j = j;
        this.j.timeout = 0;
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
}
