/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import static org.junit.Assume.assumeTrue;

import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;

import java.util.logging.Level;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.LoggerRule;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.shaded.org.apache.commons.lang3.SystemUtils;

import io.opentelemetry.api.GlobalOpenTelemetry;

/**
 * Base class for integration tests using an Elastic Stack.
 */
public abstract class ElasticStackIT {

    public static ElasticStack elasticStack;

    @Rule
    public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Rule
    public LoggerRule loggerRule = new LoggerRule()
        .record(ElasticsearchLogStorageRetriever.class, Level.FINE);

    @Rule
    public BuildWatcher buildWatcher = new BuildWatcher();

    @Before
    public void beforeEach() throws Exception {
        elasticStack.getServicePort(ElasticStack.EDOT_SERVICE, ElasticStack.OTEL_PORT);
    }

    @BeforeClass
    public static void beforeAll() throws Exception {
        assumeTrue(SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_LINUX);
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
        elasticStack = new ElasticStack();
        elasticStack.start();
        elasticStack.createLogIndex();
    }

    @AfterClass
    public static void afterAll() {
        if (elasticStack != null) {
            elasticStack.stop();
        }
        GlobalOpenTelemetry.resetForTest();
    }
}
