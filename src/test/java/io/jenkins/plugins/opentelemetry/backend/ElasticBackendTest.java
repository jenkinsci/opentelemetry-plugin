/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticLogsBackendWithJenkinsVisualization;
import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticLogsBackendWithoutJenkinsVisualization;
import io.jenkins.plugins.opentelemetry.backend.elastic.NoElasticLogsBackend;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

public class ElasticBackendTest {

    @Test
    public void testNoElasticLogsBackend() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setElasticLogsBackend(new NoElasticLogsBackend());
        Map<String, String> actual = elasticBackend.getOtelConfigurationProperties();
        Map<String, String> expected = Collections.emptyMap();
        Assert.assertEquals(actual, expected);
    }

    @Test
    public void testElasticLogsBackendWithJenkinsVisualization() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setElasticLogsBackend(new ElasticLogsBackendWithJenkinsVisualization());
        Map<String, String> actual = elasticBackend.getOtelConfigurationProperties();
        Map<String, String> expected = Collections.singletonMap("otel.logs.exporter", "otlp");
        Assert.assertEquals(actual, expected);
    }

    @Test
    public void testElasticLogsBackendWithoutJenkinsVisualization() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setElasticLogsBackend(new ElasticLogsBackendWithoutJenkinsVisualization());
        Map<String, String> actual = elasticBackend.getOtelConfigurationProperties();
        Map<String, String> expected = Collections.singletonMap("otel.logs.exporter", "otlp");
        Assert.assertEquals(actual, expected);
    }
}
