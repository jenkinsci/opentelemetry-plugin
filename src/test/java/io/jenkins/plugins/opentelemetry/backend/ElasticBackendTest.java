/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import static org.junit.jupiter.api.Assertions.*;

import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticLogsBackendWithJenkinsVisualization;
import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticLogsBackendWithoutJenkinsVisualization;
import io.jenkins.plugins.opentelemetry.backend.elastic.NoElasticLogsBackend;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ElasticBackendTest {

    @Test
    void testNoElasticLogsBackend() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setElasticLogsBackend(new NoElasticLogsBackend());
        Map<String, String> actual = elasticBackend.getOtelConfigurationProperties();
        Map<String, String> expected = Collections.emptyMap();
        assertEquals(actual, expected);
    }

    @Test
    void testElasticLogsBackendWithJenkinsVisualization() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setElasticLogsBackend(new ElasticLogsBackendWithJenkinsVisualization());
        Map<String, String> actual = elasticBackend.getOtelConfigurationProperties();
        Map<String, String> expected = Collections.singletonMap("otel.logs.exporter", "otlp");
        assertEquals(actual, expected);
    }

    @Test
    void testElasticLogsBackendWithoutJenkinsVisualization() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setElasticLogsBackend(new ElasticLogsBackendWithoutJenkinsVisualization());
        Map<String, String> actual = elasticBackend.getOtelConfigurationProperties();
        Map<String, String> expected = Collections.singletonMap("otel.logs.exporter", "otlp");
        assertEquals(actual, expected);
    }

    @Test
    void testGetKibanaBaseUrlRemovesTrailingSlash() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setKibanaBaseUrl("http://localhost:5601/");
        assertEquals("http://localhost:5601", elasticBackend.getKibanaBaseUrl());
    }

    @Test
    void testGetKibanaBaseUrlReturnsNullIfUnset() {
        ElasticBackend elasticBackend = new ElasticBackend();
        assertNull(elasticBackend.getKibanaBaseUrl());
    }

    @Test
    void testGetEffectiveKibanaURLWithSpaceIdentifier() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setKibanaBaseUrl("http://localhost:5601");
        elasticBackend.setKibanaSpaceIdentifier("my-space");
        assertEquals("http://localhost:5601/s/my-space", elasticBackend.getEffectiveKibanaURL());
    }

    @Test
    void testGetEffectiveKibanaURLWithoutSpaceIdentifier() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setKibanaBaseUrl("http://localhost:5601");
        elasticBackend.setKibanaSpaceIdentifier("");
        assertEquals("http://localhost:5601", elasticBackend.getEffectiveKibanaURL());
    }

    @Test
    void testGetMetricsVisualizationUrlTemplateWhenDisplayLinkFalse() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setDisplayKibanaDashboardLink(false);
        assertNull(elasticBackend.getMetricsVisualizationUrlTemplate());
    }

    @Test
    void testGetMetricsVisualizationUrlTemplateWhenDisplayLinkTrue() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setDisplayKibanaDashboardLink(true);
        elasticBackend.setKibanaBaseUrl("http://localhost:5601");
        elasticBackend.setKibanaSpaceIdentifier("space");
        elasticBackend.setKibanaDashboardUrlParameters("foo=bar");
        String expected = "http://localhost:5601/s/space/app/kibana#/dashboards?foo=bar";
        assertEquals(expected, elasticBackend.getMetricsVisualizationUrlTemplate());
    }

    @Test
    void testGetTraceVisualisationUrlTemplateWithEDOTDisabled() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setKibanaBaseUrl("http://localhost:5601");
        elasticBackend.setKibanaSpaceIdentifier("space");
        elasticBackend.setEnableEDOT(false);
        String url = elasticBackend.getTraceVisualisationUrlTemplate();
        assertTrue(url.contains("transactionType=job"));
        assertTrue(
                url.startsWith("http://localhost:5601/s/space/app/apm/services/${serviceName}/transactions/view"));
    }

    @Test
    void testGetTraceVisualisationUrlTemplateWithEDOTEnabled() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setKibanaBaseUrl("http://localhost:5601");
        elasticBackend.setKibanaSpaceIdentifier("space");
        elasticBackend.setEnableEDOT(true);
        String url = elasticBackend.getTraceVisualisationUrlTemplate();
        assertTrue(url.contains("transactionType=unknown"));
    }

    @Test
    void testGetBindings() {
        ElasticBackend elasticBackend = new ElasticBackend();
        elasticBackend.setKibanaBaseUrl("http://localhost:5601");
        elasticBackend.setKibanaSpaceIdentifier("space");
        elasticBackend.setKibanaDashboardTitle("My Dashboard");
        Map<String, Object> bindings = elasticBackend.getBindings();
        assertEquals("Elastic Observability", bindings.get("backendName"));
        assertEquals("/plugin/opentelemetry/images/24x24/elastic.png", bindings.get("backend24x24IconUrl"));
        assertEquals("http://localhost:5601", bindings.get("kibanaBaseUrl"));
        assertEquals("My+Dashboard", bindings.get("kibanaDashboardTitle"));
        assertEquals("space", bindings.get("kibanaSpaceIdentifier"));
    }

    @Test
    void testEqualsAndHashCode() {
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

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testNotEquals() {
        ElasticBackend a = new ElasticBackend();
        a.setDisplayKibanaDashboardLink(true);
        a.setKibanaBaseUrl("http://localhost:5601");

        ElasticBackend b = new ElasticBackend();
        b.setDisplayKibanaDashboardLink(false);
        b.setKibanaBaseUrl("http://localhost:5601");

        assertNotEquals(a, b);
    }
}
