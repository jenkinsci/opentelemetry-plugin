/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static io.jenkins.plugins.casc.misc.Util.getUnclassifiedRoot;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.github.rutledgepaulv.prune.Tree;
import hudson.model.Result;
import hudson.util.Secret;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.opentest4j.AssertionFailedError;

/**
 * The credential use events are controlled by two settings that are off by default.
 */
public class JenkinsOtelPluginCredentialEventsSettingsIntegrationTest extends BaseIntegrationTest {

    private static final String PLANTED_SECRET = "planted-secret-7f3a91c4";

    @Test
    public void testSettingsAreOffByDefault() {
        JenkinsOpenTelemetryPluginConfiguration configuration = JenkinsOpenTelemetryPluginConfiguration.get();

        assertFalse(configuration.isRecordCredentialUseEvents());
        assertFalse(configuration.isIncludeCredentialIds());
    }

    @Test
    public void testSettingsAreSetThroughTheConfigurationForm() throws Exception {
        try (JenkinsRule.WebClient webClient = jenkinsRule.createWebClient()) {
            HtmlPage page = webClient.goTo("configure");
            HtmlForm form = page.getFormByName("config");
            form.getInputByName("_.recordCredentialUseEvents").setChecked(true);
            form.getInputByName("_.includeCredentialIds").setChecked(true);
            jenkinsRule.submit(form);
        }

        JenkinsOpenTelemetryPluginConfiguration configuration = JenkinsOpenTelemetryPluginConfiguration.get();
        assertTrue(configuration.isRecordCredentialUseEvents());
        assertTrue(configuration.isIncludeCredentialIds());
    }

    @Test
    public void testSettingsSurviveTheConfigurationForm() throws Exception {
        JenkinsOpenTelemetryPluginConfiguration configuration = JenkinsOpenTelemetryPluginConfiguration.get();
        configuration.setRecordCredentialUseEvents(true);
        configuration.setIncludeCredentialIds(true);

        jenkinsRule.configRoundtrip();

        configuration = JenkinsOpenTelemetryPluginConfiguration.get();
        assertTrue(configuration.isRecordCredentialUseEvents());
        assertTrue(configuration.isIncludeCredentialIds());
    }

    @Test
    public void testConfigurationAsCodeImportThenExportKeepsTheSettings() throws Exception {
        ConfigurationAsCode.get()
                .configure(getClass()
                        .getResource("jcasc/credential-events-settings.yml")
                        .toExternalForm());

        JenkinsOpenTelemetryPluginConfiguration configuration = JenkinsOpenTelemetryPluginConfiguration.get();
        assertTrue(configuration.isRecordCredentialUseEvents());
        assertTrue(configuration.isIncludeCredentialIds());

        ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
        CNode exportedConfiguration = getUnclassifiedRoot(context).get("openTelemetry");
        String exported = toYamlString(exportedConfiguration);
        assertTrue(exported.contains("includeCredentialIds: true\n"), exported);
        assertTrue(exported.contains("recordCredentialUseEvents: true\n"), exported);
    }

    @Test
    public void testNoEventAndTheSameSpansWhenNothingIsConfigured() throws Exception {
        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(
                        Domain.global(),
                        List.of(new StringCredentialsImpl(
                                CredentialsScope.GLOBAL,
                                "planted-token",
                                "a token",
                                Secret.fromString(PLANTED_SECRET))));
        String pipelineScript = """
                node() {
                    stage('ze-stage') {
                        withCredentials([string(credentialsId: 'planted-token', variable: 'ZE_TOKEN')]) {
                            if (isUnix()) { sh label: 'ze-shell', script: 'echo ze-echo' }
                            else { bat label: 'ze-shell', script: 'echo ze-echo' }
                        }
                    }
                }
                """;
        jenkinsRule.createOnlineSlave();
        final String jobName = "test-block-steps-default-off-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));

        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        assertFalse(JenkinsOpenTelemetryPluginConfiguration.get().isRecordCredentialUseEvents());
        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpansUpToRunPhase(spans, "ze-shell", "Stage: ze-stage", ExtendedJenkinsAttributes.AGENT_UI);
        // The whole tree of the build before the settings existed: each span name with the names of its children
        Map<String, List<String>> expectedTree = new TreeMap<>();
        expectedTree.put(
                ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName,
                List.of("Phase: Finalise", "Phase: Run", "Phase: Start"));
        expectedTree.put("Phase: Start", List.of());
        expectedTree.put("Phase: Run", List.of("Agent"));
        expectedTree.put("Agent", List.of("Agent Allocation", "Stage: ze-stage"));
        expectedTree.put("Agent Allocation", List.of());
        expectedTree.put("Stage: ze-stage", List.of("ze-shell"));
        expectedTree.put("ze-shell", List.of());
        expectedTree.put("Phase: Finalise", List.of());
        Map<String, List<String>> actualTree = new TreeMap<>();
        getSpanMapWithChildrenFromTree(spans)
                .forEach((name, children) ->
                        actualTree.put(name, children.stream().sorted().toList()));
        assertEquals(expectedTree, actualTree);
        assertNoCredentialUseEvent();
        assertNoSecretInTrace(PLANTED_SECRET);
    }

    private void assertNoCredentialUseEvent() {
        jenkinsControllerOpenTelemetry
                .getOpenTelemetrySdk()
                .getSdkTracerProvider()
                .forceFlush()
                .join(1, TimeUnit.SECONDS);
        for (SpanData span : InMemorySpanExporterProvider.LAST_CREATED_INSTANCE.getFinishedSpanItems()) {
            span.getEvents()
                    .forEach(event -> assertFalse(
                            "jenkins.credentials.use".equals(event.getName()),
                            "Credential use event on span " + span.getName()));
        }
    }

    @Test
    public void testSecretInASpanAttributeIsFound() {
        jenkinsControllerOpenTelemetry
                .getOpenTelemetrySdk()
                .getTracer("credential-events-settings-test")
                .spanBuilder("span-with-a-secret")
                .setAttribute("some.attribute", "value-" + PLANTED_SECRET)
                .startSpan()
                .end();

        AssertionFailedError error =
                assertThrows(AssertionFailedError.class, () -> assertNoSecretInTrace(PLANTED_SECRET));

        assertTrue(error.getMessage().contains("span-with-a-secret"));
        assertFalse(error.getMessage().contains(PLANTED_SECRET));
    }
}
