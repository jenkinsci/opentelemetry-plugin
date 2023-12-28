/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.opentelemetry.rules.CheckIsDockerAvailable;
import io.jenkins.plugins.opentelemetry.rules.CheckIsLinuxOrMac;
import io.opentelemetry.api.GlobalOpenTelemetry;

/**
 * Base class for integration tests using an Elastic Stack.
 */
public abstract class ElasticStackIT {
    @ClassRule
    @ConfiguredWithCode("jcasc-elastic-backend.yml")
    public static JenkinsConfiguredWithCodeRule jenkinsRule = new JenkinsConfiguredWithCodeRule();

    public ElasticStack elasticStack;

    @ClassRule
    public static CheckIsLinuxOrMac isLinuxOrMac = new CheckIsLinuxOrMac();

    @ClassRule
    public static CheckIsDockerAvailable isDockerAvailable = new CheckIsDockerAvailable();

    @BeforeClass
    public static void beforeClass() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterClass
    public static void afterClass() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Before
    public void setUp() throws Exception {
        elasticStack = new ElasticStack();
        elasticStack.start();
        elasticStack.configureElasticBackEnd();
    }
}
