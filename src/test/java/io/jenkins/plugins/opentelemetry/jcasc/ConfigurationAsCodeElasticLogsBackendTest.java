/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.jcasc;

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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static io.jenkins.plugins.casc.misc.Util.getUnclassifiedRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.hamcrest.CoreMatchers.instanceOf;

public class ConfigurationAsCodeElasticLogsBackendTest {

    @ClassRule
    @ConfiguredWithCode("configuration-as-code-elastic-logs-backend.yml")
    public static JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Test
    public void should_support_configuration_as_code() {
        final JenkinsOpenTelemetryPluginConfiguration configuration = GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class);

        MatcherAssert.assertThat(configuration.getEndpoint(), CoreMatchers.is("http://otel-collector-contrib:4317"));

        ElasticBackend elastic = (ElasticBackend) configuration.getObservabilityBackends().get(0);
        MatcherAssert.assertThat(elastic.getKibanaBaseUrl(), CoreMatchers.is("http://localhost:5601"));
        MatcherAssert.assertThat(elastic.getName(), CoreMatchers.is("My Elastic"));

        ElasticLogsBackendWithJenkinsVisualization elasticLogsBackend = (ElasticLogsBackendWithJenkinsVisualization) elastic.getElasticLogsBackend();
        MatcherAssert.assertThat(elasticLogsBackend.getElasticsearchCredentialsId(), CoreMatchers.is("elasticsearch-logs-creds"));
        MatcherAssert.assertThat(elasticLogsBackend.getElasticsearchUrl(), CoreMatchers.is("http://localhost:9200"));

        OtlpAuthentication authentication = configuration.getAuthentication();
        MatcherAssert.assertThat(authentication, CoreMatchers.is(instanceOf(NoAuthentication.class)));

        MatcherAssert.assertThat(configuration.getExporterTimeoutMillis(), CoreMatchers.nullValue());
        MatcherAssert.assertThat(configuration.getExporterIntervalMillis(), CoreMatchers.nullValue());

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

        String expected = toStringFromYamlFile(this, "configuration-as-code-elastic-logs-backend-expected.yml");

        MatcherAssert.assertThat(exported, CoreMatchers.is(expected));
    }

    @BeforeClass
    public static void beforeClass() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterClass
    public static void afterClass() {
        GlobalOpenTelemetry.resetForTest();
    }
}
