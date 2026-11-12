/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class GrafanaBackendTest {

    @Test
    public void testTraceUrl() {
        GrafanaBackend grafanaBackend = new GrafanaBackend();
        grafanaBackend.setGrafanaBaseUrl("https://cleclerc.grafana.net");
        grafanaBackend.setGrafanaOrgId("1");
        grafanaBackend.setTempoDataSourceUid("my-awesome-custom-uid");
        grafanaBackend.setTempoQueryType("traceql");

        LocalDateTime buildTime =
                LocalDateTime.parse("2023-02-05 23:31:52.610", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("serviceName", "jenkins");
        bindings.put("rootSpanName", "BUILD my-app");
        bindings.put("traceId", "f464e1f32444443d3fc00fdb19e5c124");
        bindings.put("spanId", "00799ea60984f33f");
        bindings.put("startTime", buildTime);

        String actualUrl = grafanaBackend.getTraceVisualisationUrl(bindings);
        System.out.println(actualUrl);

        assertTrue(Objects.requireNonNull(actualUrl).contains("schemaVersion=1"), "URL must contain schemaVersion=1");
        assertTrue(actualUrl.contains("panes="), "URL must use 'panes' instead of 'left'");
        assertTrue(actualUrl.contains("my-awesome-custom-uid"), "URL must contain the new Tempo UID");
        assertTrue(actualUrl.contains("f464e1f32444443d3fc00fdb19e5c124"), "URL must contain the exact traceId");
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testTraceUrlFallbackToIdentifier() {
        GrafanaBackend grafanaBackend = new GrafanaBackend();
        grafanaBackend.setGrafanaBaseUrl("https://cleclerc.grafana.net");
        grafanaBackend.setGrafanaOrgId("1");
        grafanaBackend.setTempoDataSourceIdentifier("grafanacloud-traces");
        grafanaBackend.setTempoQueryType("traceql");

        LocalDateTime buildTime =
                LocalDateTime.parse("2023-02-05 23:31:52.610", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("serviceName", "jenkins");
        bindings.put("rootSpanName", "BUILD my-app");
        bindings.put("traceId", "f464e1f32444443d3fc00fdb19e5c124");
        bindings.put("spanId", "00799ea60984f33f");
        bindings.put("startTime", buildTime);

        String actualUrl = grafanaBackend.getTraceVisualisationUrl(bindings);

        assertTrue(Objects.requireNonNull(actualUrl).contains("schemaVersion=1"), "URL must contain schemaVersion=1");
        assertTrue(actualUrl.contains("panes="), "URL must use 'panes' instead of 'left'");
        assertTrue(
                actualUrl.contains("grafanacloud-traces"),
                "URL must fall back to the tempoDataSourceIdentifier when UID is missing");
        assertTrue(actualUrl.contains("f464e1f32444443d3fc00fdb19e5c124"), "URL must contain the exact traceId");
    }

    @Test
    public void testEqualsAndHashCode() {
        GrafanaBackend backend1 = new GrafanaBackend();
        backend1.setGrafanaBaseUrl("http://grafana");
        backend1.setGrafanaOrgId("1");
        backend1.setTempoDataSourceUid("tempo-uid");
        backend1.setTempoQueryType("traceql");

        GrafanaBackend backend2 = new GrafanaBackend();
        backend2.setGrafanaBaseUrl("http://grafana");
        backend2.setGrafanaOrgId("1");
        backend2.setTempoDataSourceUid("tempo-uid");
        backend2.setTempoQueryType("traceql");

        assertEquals(backend1, backend2);
        assertEquals(backend1.hashCode(), backend2.hashCode());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testReadResolveMigratesIdentifierToUid() {
        GrafanaBackend backend = new GrafanaBackend();

        backend.setTempoDataSourceIdentifier("old-datasource-id");
        backend.readResolve();

        assertEquals("old-datasource-id", backend.getTempoDataSourceUid());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testReadResolveDoesNotOverrideExistingUid() {
        GrafanaBackend backend = new GrafanaBackend();

        backend.setTempoDataSourceIdentifier("old-id");
        backend.setTempoDataSourceUid("new-uid");
        backend.readResolve();

        assertEquals("new-uid", backend.getTempoDataSourceUid());
    }

    @Test
    public void testDescriptorDefaultTempoDataSourceUid() {
        GrafanaBackend.DescriptorImpl descriptor = new GrafanaBackend.DescriptorImpl();

        assertEquals("grafanacloud-traces", descriptor.getDefaultTempoDataSourceUid());
    }

    @Test
    public void testDescriptorDefaults() {
        GrafanaBackend.DescriptorImpl descriptor = new GrafanaBackend.DescriptorImpl();

        assertEquals("grafanacloud-traces", descriptor.getDefaultTempoDataSourceUid());
        assertEquals("1", descriptor.getDefaultGrafanaOrgId());
        assertEquals("traceql", descriptor.getDefaultTempoQueryType());
    }
}
