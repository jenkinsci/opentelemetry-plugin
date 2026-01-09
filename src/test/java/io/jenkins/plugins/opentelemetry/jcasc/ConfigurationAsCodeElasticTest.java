/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.jcasc;

import static io.jenkins.plugins.casc.misc.Util.getUnclassifiedRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.authentication.BearerTokenAuthentication;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.opentelemetry.api.GlobalOpenTelemetry;
import jenkins.model.GlobalConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class ConfigurationAsCodeElasticTest {

    @Test
    @ConfiguredWithCode("elastic.yml")
    void should_support_configuration_as_code(JenkinsConfiguredWithCodeRule j) {
        final JenkinsOpenTelemetryPluginConfiguration configuration =
                GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class);

        assertThat(
                configuration.getEndpoint(), is("https://my-deployment.otel.example.com"));
        assertThat(configuration.getObservabilityBackends().size(), is(1));

        ElasticBackend elastic =
                (ElasticBackend) configuration.getObservabilityBackends().get(0);
        assertThat(elastic.getKibanaBaseUrl(), is("https://my-deployment.es.example.com"));
        assertThat(elastic.getName(), is("My Elastic"));

        BearerTokenAuthentication authentication = (BearerTokenAuthentication) configuration.getAuthentication();
        assertThat(authentication.getTokenId(), is("apm-server-token"));

        assertThat(configuration.getServiceName(), is("my-jenkins"));
        assertThat(configuration.getServiceNamespace(), is("ci"));
    }

    @Test
    @ConfiguredWithCode("elastic.yml")
    void should_support_configuration_export(JenkinsConfiguredWithCodeRule j) throws Exception {
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode yourAttribute = getUnclassifiedRoot(context).get("openTelemetry");

        String exported = toYamlString(yourAttribute);

        String expected = toStringFromYamlFile(this, "elastic-expected.yml");

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
