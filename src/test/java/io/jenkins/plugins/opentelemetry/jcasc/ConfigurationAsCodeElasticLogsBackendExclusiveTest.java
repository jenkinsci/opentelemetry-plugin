/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.jcasc;

import static io.jenkins.plugins.casc.misc.Util.getUnclassifiedRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.authentication.NoAuthentication;
import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticLogsBackendWithoutJenkinsVisualization;
import io.opentelemetry.api.GlobalOpenTelemetry;
import jenkins.model.GlobalConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class ConfigurationAsCodeElasticLogsBackendExclusiveTest {

    @Test
    @ConfiguredWithCode("elastic-logs-exclusive.yml")
    void should_support_configuration_as_code(JenkinsConfiguredWithCodeRule j) {
        final JenkinsOpenTelemetryPluginConfiguration configuration =
                GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class);

        assertThat(configuration.getEndpoint(), is("http://otel-collector-contrib:4317"));

        ElasticBackend elastic =
                (ElasticBackend) configuration.getObservabilityBackends().get(0);
        assertThat(elastic.getKibanaBaseUrl(), is("https://kibana.es.example.com"));
        assertThat(elastic.getName(), is("My Elastic"));

        ElasticLogsBackendWithoutJenkinsVisualization elasticLogsBackend =
                (ElasticLogsBackendWithoutJenkinsVisualization) elastic.getElasticLogsBackend();
        assertThat(elasticLogsBackend, is(notNullValue()));

        OtlpAuthentication authentication = configuration.getAuthentication();
        assertThat(authentication, is(instanceOf(NoAuthentication.class)));

        assertThat(configuration.getIgnoredSteps(), is("dir,echo,isUnix,pwd,properties"));

        assertThat(configuration.getServiceName(), is("jenkins"));
        assertThat(configuration.getServiceNamespace(), is("jenkins"));
    }

    @Test
    @ConfiguredWithCode("elastic-logs-exclusive.yml")
    void should_support_configuration_export(JenkinsConfiguredWithCodeRule j) throws Exception {
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode yourAttribute = getUnclassifiedRoot(context).get("openTelemetry");

        String exported = toYamlString(yourAttribute);

        String expected = toStringFromYamlFile(this, "elastic-logs-exclusive-expected.yml");

        assertThat(exported, is(expected));
    }

    @BeforeAll
    static void beforeClass() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterAll
    static void afterClass() {
        GlobalOpenTelemetry.resetForTest();
    }
}
