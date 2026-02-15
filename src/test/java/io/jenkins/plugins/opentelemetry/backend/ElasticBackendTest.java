/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticLogsBackendWithJenkinsVisualization;
import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticLogsBackendWithoutJenkinsVisualization;
import io.jenkins.plugins.opentelemetry.backend.elastic.NoElasticLogsBackend;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ElasticBackendTest {

    @Test
    public void testNoElasticLogsBackend() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setElasticLogsBackend(new NoElasticLogsBackend());
        Map<String, String> actual = elasticBackend.getOtelConfigurationProperties();
        Map<String, String> expected = Collections.emptyMap();
        Assertions.assertEquals(actual, expected);
    }

    @Test
    public void testElasticLogsBackendWithJenkinsVisualization() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setElasticLogsBackend(new ElasticLogsBackendWithJenkinsVisualization());
        Map<String, String> actual = elasticBackend.getOtelConfigurationProperties();
        Map<String, String> expected = Collections.singletonMap("otel.logs.exporter", "otlp");
        Assertions.assertEquals(actual, expected);
    }

    @Test
    public void testElasticLogsBackendWithoutJenkinsVisualization() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setElasticLogsBackend(new ElasticLogsBackendWithoutJenkinsVisualization());
        Map<String, String> actual = elasticBackend.getOtelConfigurationProperties();
        Map<String, String> expected = Collections.singletonMap("otel.logs.exporter", "otlp");
        Assertions.assertEquals(actual, expected);
    }

    @Test
    public void testGetKibanaBaseUrlRemovesTrailingSlash() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setKibanaBaseUrl("http://localhost:5601/");
        Assertions.assertEquals("http://localhost:5601", elasticBackend.getKibanaBaseUrl());
    }

    @Test
    public void testGetKibanaBaseUrlReturnsNullIfUnset() {
        ElasticBackend elasticBackend = new ElasticBackend();
        Assertions.assertNull(elasticBackend.getKibanaBaseUrl());
    }

    @Test
    public void testGetEffectiveKibanaURLWithSpaceIdentifier() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setKibanaBaseUrl("http://localhost:5601");
        elasticBackend.setKibanaSpaceIdentifier("my-space");
        Assertions.assertEquals("http://localhost:5601/s/my-space", elasticBackend.getEffectiveKibanaURL());
    }

    @Test
    public void testGetEffectiveKibanaURLWithoutSpaceIdentifier() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setKibanaBaseUrl("http://localhost:5601");
        elasticBackend.setKibanaSpaceIdentifier("");
        Assertions.assertEquals("http://localhost:5601", elasticBackend.getEffectiveKibanaURL());
    }

    @Test
    public void testGetMetricsVisualizationUrlTemplateWhenDisplayLinkFalse() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setDisplayKibanaDashboardLink(false);
        Assertions.assertNull(elasticBackend.getMetricsVisualizationUrlTemplate());
    }

    @Test
    public void testGetMetricsVisualizationUrlTemplateWhenDisplayLinkTrue() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setDisplayKibanaDashboardLink(true);
        elasticBackend.setKibanaBaseUrl("http://localhost:5601");
        elasticBackend.setKibanaSpaceIdentifier("space");
        elasticBackend.setKibanaDashboardUrlParameters("foo=bar");
        String expected = "http://localhost:5601/s/space/app/kibana#/dashboards?foo=bar";
        Assertions.assertEquals(expected, elasticBackend.getMetricsVisualizationUrlTemplate());
    }

    @Test
    public void testGetTraceVisualisationUrlTemplateWithEDOTDisabled() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setKibanaBaseUrl("http://localhost:5601");
        elasticBackend.setKibanaSpaceIdentifier("space");
        elasticBackend.setEnableEDOT(false);
        String url = elasticBackend.getTraceVisualisationUrlTemplate();
        Assertions.assertTrue(url.contains("transactionType=job"));
        Assertions.assertTrue(
                url.startsWith("http://localhost:5601/s/space/app/apm/services/${serviceName}/transactions/view"));
    }

    @Test
    public void testGetTraceVisualisationUrlTemplateWithEDOTEnabled() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setKibanaBaseUrl("http://localhost:5601");
        elasticBackend.setKibanaSpaceIdentifier("space");
        elasticBackend.setEnableEDOT(true);
        String url = elasticBackend.getTraceVisualisationUrlTemplate();
        Assertions.assertTrue(url.contains("transactionType=unknown"));
    }

    @Test
    public void testGetBindings() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setKibanaBaseUrl("http://localhost:5601");
        elasticBackend.setKibanaSpaceIdentifier("space");
        elasticBackend.setKibanaDashboardTitle("My Dashboard");
        Map<String, Object> bindings = elasticBackend.getBindings();
        Assertions.assertEquals("Elastic Observability", bindings.get("backendName"));
        Assertions.assertEquals("/plugin/opentelemetry/images/24x24/elastic.png", bindings.get("backend24x24IconUrl"));
        Assertions.assertEquals("http://localhost:5601", bindings.get("kibanaBaseUrl"));
        Assertions.assertEquals("My+Dashboard", bindings.get("kibanaDashboardTitle"));
        Assertions.assertEquals("space", bindings.get("kibanaSpaceIdentifier"));
    }

    @Test
    public void testEqualsAndHashCode() {
        ElasticBackend a = new ElasticBackend();
        a.setDisplayKibanaDashboardLink(true);
        a.setKibanaBaseUrl("http://localhost:5601");
        a.setKibanaSpaceIdentifier("space");
        a.setKibanaDashboardTitle("title");
        a.setKibanaDashboardUrlParameters("params");

        ElasticBackend b = new ElasticBackend();
        b.setDisplayKibanaDashboardLink(true);
        b.setKibanaBaseUrl("http://localhost:5601");
        b.setKibanaSpaceIdentifier("space");
        b.setKibanaDashboardTitle("title");
        b.setKibanaDashboardUrlParameters("params");

        Assertions.assertEquals(a, b);
        Assertions.assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testNotEquals() {
        ElasticBackend a = new ElasticBackend();
        a.setDisplayKibanaDashboardLink(true);
        a.setKibanaBaseUrl("http://localhost:5601");

        ElasticBackend b = new ElasticBackend();
        b.setDisplayKibanaDashboardLink(false);
        b.setKibanaBaseUrl("http://localhost:5601");

        Assertions.assertNotEquals(a, b);
    }
}
