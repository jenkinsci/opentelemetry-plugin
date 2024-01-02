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
import io.jenkins.plugins.opentelemetry.authentication.BearerTokenAuthentication;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.opentelemetry.api.GlobalOpenTelemetry;
import jenkins.model.GlobalConfiguration;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.*;

import static io.jenkins.plugins.casc.misc.Util.getUnclassifiedRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;

public class ConfigurationAsCodeElasticTest {

    @ClassRule
    @ConfiguredWithCode("elastic.yml")
    public static JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Test
    public void should_support_configuration_as_code() {
        final JenkinsOpenTelemetryPluginConfiguration configuration = GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class);

        MatcherAssert.assertThat(configuration.getEndpoint(), CoreMatchers.is("https://my-deployment.otel.example.com"));
        MatcherAssert.assertThat(configuration.getObservabilityBackends().size(), CoreMatchers.is(1));

        ElasticBackend elastic = (ElasticBackend) configuration.getObservabilityBackends().get(0);
        MatcherAssert.assertThat(elastic.getKibanaBaseUrl(), CoreMatchers.is("https://my-deployment.es.example.com"));
        MatcherAssert.assertThat(elastic.getName(), CoreMatchers.is("My Elastic"));

        BearerTokenAuthentication authentication = (BearerTokenAuthentication) configuration.getAuthentication();
        MatcherAssert.assertThat(authentication.getTokenId(), CoreMatchers.is("apm-server-token"));

        MatcherAssert.assertThat(configuration.getServiceName(), CoreMatchers.is("my-jenkins"));
        MatcherAssert.assertThat(configuration.getServiceNamespace(), CoreMatchers.is("ci"));

        MatcherAssert.assertThat(configuration.getExporterIntervalMillis(), CoreMatchers.is(Integer.valueOf(60_000)));
        MatcherAssert.assertThat(configuration.getExporterTimeoutMillis(), CoreMatchers.is(Integer.valueOf(30_000)));
    }

    @Test
    public void should_support_configuration_export() throws Exception {
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode yourAttribute = getUnclassifiedRoot(context).get("openTelemetry");

        String exported = toYamlString(yourAttribute);

        String expected = toStringFromYamlFile(this, "elastic-expected.yml");

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
