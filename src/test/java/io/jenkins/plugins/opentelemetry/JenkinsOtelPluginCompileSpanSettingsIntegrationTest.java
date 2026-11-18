/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static io.jenkins.plugins.casc.misc.Util.getUnclassifiedRoot;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.model.CNode;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * The compile span is controlled by a setting that is off by default.
 */
public class JenkinsOtelPluginCompileSpanSettingsIntegrationTest extends BaseIntegrationTest {

    @Test
    public void testSettingIsOffByDefault() {
        JenkinsOpenTelemetryPluginConfiguration configuration = JenkinsOpenTelemetryPluginConfiguration.get();

        assertFalse(configuration.isRecordCompileSpan());
    }

    @Test
    public void testSettingIsSetThroughTheConfigurationForm() throws Exception {
        try (JenkinsRule.WebClient webClient = jenkinsRule.createWebClient()) {
            HtmlPage page = webClient.goTo("configure");
            HtmlForm form = page.getFormByName("config");
            form.getInputByName("_.recordCompileSpan").setChecked(true);
            jenkinsRule.submit(form);
        }

        JenkinsOpenTelemetryPluginConfiguration configuration = JenkinsOpenTelemetryPluginConfiguration.get();
        assertTrue(configuration.isRecordCompileSpan());
    }

    @Test
    public void testSettingSurvivesTheConfigurationForm() throws Exception {
        JenkinsOpenTelemetryPluginConfiguration configuration = JenkinsOpenTelemetryPluginConfiguration.get();
        configuration.setRecordCompileSpan(true);

        jenkinsRule.configRoundtrip();

        configuration = JenkinsOpenTelemetryPluginConfiguration.get();
        assertTrue(configuration.isRecordCompileSpan());
    }

    @Test
    public void testConfigurationAsCodeImportThenExportKeepsTheSetting() throws Exception {
        ConfigurationAsCode.get()
                .configure(getClass()
                        .getResource("jcasc/compile-span-settings.yml")
                        .toExternalForm());

        JenkinsOpenTelemetryPluginConfiguration configuration = JenkinsOpenTelemetryPluginConfiguration.get();
        assertTrue(configuration.isRecordCompileSpan());

        ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
        CNode exportedConfiguration = getUnclassifiedRoot(context).get("openTelemetry");
        String exported = toYamlString(exportedConfiguration);
        assertTrue(exported.contains("recordCompileSpan: true\n"), exported);
    }
}
