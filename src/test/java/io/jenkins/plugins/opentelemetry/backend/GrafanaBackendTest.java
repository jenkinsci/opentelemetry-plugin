/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class GrafanaBackendTest {

    @Test
    public void testTraceUrl() {
        GrafanaBackend grafanaBackend = new GrafanaBackend();
        grafanaBackend.setGrafanaBaseUrl("https://cleclerc.grafana.net");
        grafanaBackend.setGrafanaOrgId("1");
        grafanaBackend.setTempoDataSourceIdentifier("grafanacloud-traces");

        LocalDateTime buildTime = LocalDateTime.parse("2023-02-05 23:31:52.610", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("serviceName", "jenkins");
        bindings.put("rootSpanName", "BUILD my-app");
        bindings.put("traceId", "f464e1f32444443d3fc00fdb19e5c124");
        bindings.put("spanId", "00799ea60984f33f");
        bindings.put("startTime", buildTime);

        String actualTraceVisualisationUrl = grafanaBackend.getTraceVisualisationUrl(bindings);
        System.out.println(actualTraceVisualisationUrl);
    }
}
