/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.jcasc;

import static io.jenkins.plugins.casc.misc.Util.getUnclassifiedRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.hamcrest.CoreMatchers.instanceOf;

import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.authentication.NoAuthentication;
import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticLogsBackendWithJenkinsVisualization;
import io.opentelemetry.api.GlobalOpenTelemetry;
import jenkins.model.GlobalConfiguration;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ConfigurationAsCodeElasticLogsBackendTest {

    @RegisterExtension
    @ConfiguredWithCode("elastic-logs.yml")
    public static JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Test
    public void should_support_configuration_as_code() {
        final JenkinsOpenTelemetryPluginConfiguration configuration =
                GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class);

        MatcherAssert.assertThat(configuration.getEndpoint(), CoreMatchers.is("http://otel-collector-contrib:4317"));

        ElasticBackend elastic =
                (ElasticBackend) configuration.getObservabilityBackends().get(0);
        MatcherAssert.assertThat(elastic.getKibanaBaseUrl(), CoreMatchers.is("https://kibana.es.example.com"));
        MatcherAssert.assertThat(elastic.getName(), CoreMatchers.is("My Elastic"));

        ElasticLogsBackendWithJenkinsVisualization elasticLogsBackend =
                (ElasticLogsBackendWithJenkinsVisualization) elastic.getElasticLogsBackend();
        MatcherAssert.assertThat(
                elasticLogsBackend.getElasticsearchCredentialsId(), CoreMatchers.is("elasticsearch-logs-creds"));
        MatcherAssert.assertThat(
                elasticLogsBackend.getElasticsearchUrl(), CoreMatchers.is("https://es.es.example.com"));

        OtlpAuthentication authentication = configuration.getAuthentication();
        MatcherAssert.assertThat(authentication, CoreMatchers.is(instanceOf(NoAuthentication.class)));

        MatcherAssert.assertThat(configuration.getIgnoredSteps(), CoreMatchers.is("dir,echo,isUnix,pwd,properties"));

        MatcherAssert.assertThat(configuration.getServiceName(), CoreMatchers.is("jenkins"));
        MatcherAssert.assertThat(configuration.getServiceNamespace(), CoreMatchers.is("jenkins"));
    }

    @Test
    public void should_support_configuration_export() throws Exception {
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode yourAttribute = getUnclassifiedRoot(context).get("openTelemetry");

        String exported = toYamlString(yourAttribute);

        String expected = toStringFromYamlFile(this, "elastic-logs-expected.yml");

        MatcherAssert.assertThat(exported, CoreMatchers.is(expected));
    }

    @BeforeAll
    public static void beforeClass() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterAll
    public static void afterClass() {
        GlobalOpenTelemetry.resetForTest();
    }
}
