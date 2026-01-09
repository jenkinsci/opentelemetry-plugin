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

import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.authentication.NoAuthentication;
import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
import io.jenkins.plugins.opentelemetry.backend.CustomObservabilityBackend;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.JaegerBackend;
import io.jenkins.plugins.opentelemetry.backend.ZipkinBackend;
import io.opentelemetry.api.GlobalOpenTelemetry;
import jenkins.model.GlobalConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class ConfigurationAsCodeTest {

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    void should_support_configuration_as_code(JenkinsConfiguredWithCodeRule j) {
        final JenkinsOpenTelemetryPluginConfiguration configuration =
                GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class);

        assertThat(configuration.getEndpoint(), is("http://otel-collector-contrib:4317"));
        assertThat(configuration.getObservabilityBackends().size(), is(4));

        ElasticBackend elastic =
                (ElasticBackend) configuration.getObservabilityBackends().get(0);
        assertThat(elastic.getKibanaBaseUrl(), is("http://localhost:5601"));
        assertThat(elastic.getName(), is("My Elastic"));

        JaegerBackend jaeger =
                (JaegerBackend) configuration.getObservabilityBackends().get(1);
        assertThat(jaeger.getJaegerBaseUrl(), is("http://localhost:16686"));
        assertThat(jaeger.getName(), is("My Jaeger"));

        CustomObservabilityBackend custom = (CustomObservabilityBackend)
                configuration.getObservabilityBackends().get(2);
        assertThat(custom.getMetricsVisualizationUrlTemplate(), is("foo"));
        assertThat(custom.getTraceVisualisationUrlTemplate(), is("http://example.com"));
        assertThat(custom.getName(), is("My Custom"));

        ZipkinBackend zipkin =
                (ZipkinBackend) configuration.getObservabilityBackends().get(3);
        assertThat(zipkin.getZipkinBaseUrl(), is("http://localhost:9411/"));
        assertThat(zipkin.getName(), is("My Zipkin"));

        OtlpAuthentication authentication = configuration.getAuthentication();
        assertThat(authentication, is(instanceOf(NoAuthentication.class)));

        assertThat(configuration.getServiceName(), is("my-jenkins"));
        assertThat(configuration.getServiceNamespace(), is("ci"));
    }

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    void should_support_configuration_export(JenkinsConfiguredWithCodeRule j) throws Exception {
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode yourAttribute = getUnclassifiedRoot(context).get("openTelemetry");

        String exported = toYamlString(yourAttribute);

        String expected = toStringFromYamlFile(this, "configuration-as-code-expected.yml");

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
