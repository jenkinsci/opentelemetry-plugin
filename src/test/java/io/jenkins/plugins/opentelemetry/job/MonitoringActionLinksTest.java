/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class MonitoringActionLinksTest {

    @Test
    public void testHideMonitoringLinksConfig(JenkinsRule j) throws Exception {
        JenkinsOpenTelemetryPluginConfiguration config = JenkinsOpenTelemetryPluginConfiguration.get();
        config.setHideMonitoringLinks(true);

        FreeStyleProject project = j.createFreeStyleProject();
        FreeStyleBuild run = j.buildAndAssertSuccess(project);

        Span span = Span.getInvalid();
        MonitoringAction action = new MonitoringAction(span);
        action.onAttached(run);

        assertTrue(action.getLinks().isEmpty(), "Links should be completely hidden when the toggle is checked");
    }

    @Test
    public void testLinksVisibleWhenToggleDisabled(JenkinsRule j) throws Exception {
        JenkinsOpenTelemetryPluginConfiguration config = JenkinsOpenTelemetryPluginConfiguration.get();
        config.setHideMonitoringLinks(false);

        FreeStyleProject project = j.createFreeStyleProject();
        FreeStyleBuild run = j.buildAndAssertSuccess(project);

        Span span = Span.getInvalid();
        MonitoringAction action = new MonitoringAction(span);
        action.onAttached(run);

        assertTrue(
                action.getLinks().get(0).getLabel().contains("Please define"),
                "Fallback message should be shown when no backend configured");
    }

    @Test
    public void testTracesExporterNoneHidesLinks(JenkinsRule j) throws Exception {
        JenkinsOpenTelemetryPluginConfiguration config = JenkinsOpenTelemetryPluginConfiguration.get();

        config.setEndpoint("http://localhost:4317");
        config.setHideMonitoringLinks(false);
        config.setConfigurationProperties("otel.traces.exporter=none");
        config.configureOpenTelemetrySdk();

        FreeStyleProject project = j.createFreeStyleProject();
        FreeStyleBuild run = j.buildAndAssertSuccess(project);

        Span span = Span.getInvalid();
        MonitoringAction action = new MonitoringAction(span);
        action.onAttached(run);

        assertTrue(action.getLinks().isEmpty(), "Links should be hidden when traces exporter is set to none");
    }

    @AfterEach
    void resetConfig() {
        JenkinsOpenTelemetryPluginConfiguration config = JenkinsOpenTelemetryPluginConfiguration.get();
        config.setHideMonitoringLinks(false);
        config.setEndpoint(null);
        config.setConfigurationProperties(null);
        config.configureOpenTelemetrySdk();
    }
}
