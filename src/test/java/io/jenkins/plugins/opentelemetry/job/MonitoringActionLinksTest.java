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

    @AfterEach
    void resetConfig() {
        JenkinsOpenTelemetryPluginConfiguration config = JenkinsOpenTelemetryPluginConfiguration.get();
        config.setHideMonitoringLinks(false);
    }
}
