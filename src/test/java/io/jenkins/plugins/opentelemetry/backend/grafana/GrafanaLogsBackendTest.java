/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import groovy.lang.Writable;
import groovy.text.Template;
import io.jenkins.plugins.opentelemetry.backend.GrafanaBackend;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.Test;

public class GrafanaLogsBackendTest {

    @Test
    public void getBuildLogsVisualizationMessageTemplate() {

        Instant startTime =
                ZonedDateTime.of(2023, 10, 15, 22, 34, 0, 0, ZoneId.of("UTC")).toInstant();

        Instant endTime = startTime.plus(5, ChronoUnit.MINUTES);

        GrafanaLogsBackend grafanaLogsBackend = new GrafanaLogsBackendWithoutJenkinsVisualization();
        Template buildLogsVisualizationUrlTemplate = grafanaLogsBackend.getBuildLogsVisualizationUrlTemplate();
        Writable logsUrl = buildLogsVisualizationUrlTemplate.make(Map.of(
                GrafanaBackend.TemplateBindings.GRAFANA_BASE_URL, "https://cleclerc.grafana.net/",
                GrafanaBackend.TemplateBindings.SERVICE_NAMESPACE_AND_NAME, "jenkins/jenkins",
                GrafanaBackend.TemplateBindings.GRAFANA_LOKI_DATASOURCE_IDENTIFIER, "grafanacloud-logs",
                GrafanaBackend.TemplateBindings.GRAFANA_ORG_ID, "1",
                GrafanaBackend.TemplateBindings.TRACE_ID, "1234567890",
                GrafanaBackend.TemplateBindings.START_TIME, startTime,
                GrafanaBackend.TemplateBindings.END_TIME, endTime));
        System.out.println(logsUrl.toString());
    }
}
