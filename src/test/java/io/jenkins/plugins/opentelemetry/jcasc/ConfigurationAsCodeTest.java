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
import io.jenkins.plugins.opentelemetry.backend.CustomObservabilityBackend;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.JaegerBackend;
import io.jenkins.plugins.opentelemetry.backend.ZipkinBackend;
import io.opentelemetry.api.GlobalOpenTelemetry;
import jenkins.model.GlobalConfiguration;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.*;

import static io.jenkins.plugins.casc.misc.Util.getUnclassifiedRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.hamcrest.CoreMatchers.instanceOf;

public class ConfigurationAsCodeTest {

    @ClassRule
    @ConfiguredWithCode("configuration-as-code.yml")
    public static JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Test
    public void should_support_configuration_as_code() {
        final JenkinsOpenTelemetryPluginConfiguration configuration = GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class);

        MatcherAssert.assertThat(configuration.getEndpoint(), CoreMatchers.is("http://otel-collector-contrib:4317"));
        MatcherAssert.assertThat(configuration.getObservabilityBackends().size(), CoreMatchers.is(4));

        ElasticBackend elastic = (ElasticBackend) configuration.getObservabilityBackends().get(0);
        MatcherAssert.assertThat(elastic.getKibanaBaseUrl(), CoreMatchers.is("http://localhost:5601"));
        MatcherAssert.assertThat(elastic.getName(), CoreMatchers.is("My Elastic"));

        JaegerBackend jaeger = (JaegerBackend) configuration.getObservabilityBackends().get(1);
        MatcherAssert.assertThat(jaeger.getJaegerBaseUrl(), CoreMatchers.is("http://localhost:16686"));
        MatcherAssert.assertThat(jaeger.getName(), CoreMatchers.is("My Jaeger"));

        CustomObservabilityBackend custom = (CustomObservabilityBackend) configuration.getObservabilityBackends().get(2);
        MatcherAssert.assertThat(custom.getMetricsVisualisationUrlTemplate(), CoreMatchers.is("foo"));
        MatcherAssert.assertThat(custom.getTraceVisualisationUrlTemplate(), CoreMatchers.is("http://example.com"));
        MatcherAssert.assertThat(custom.getName(), CoreMatchers.is("My Custom"));

        ZipkinBackend zipkin = (ZipkinBackend) configuration.getObservabilityBackends().get(3);
        MatcherAssert.assertThat(zipkin.getZipkinBaseUrl(), CoreMatchers.is("http://localhost:9411/"));
        MatcherAssert.assertThat(zipkin.getName(), CoreMatchers.is("My Zipkin"));

        OtlpAuthentication authentication = configuration.getAuthentication();
        MatcherAssert.assertThat(authentication, CoreMatchers.is(instanceOf(NoAuthentication.class)));

        MatcherAssert.assertThat(configuration.getServiceName(), CoreMatchers.is("my-jenkins"));
        MatcherAssert.assertThat(configuration.getServiceNamespace(), CoreMatchers.is("ci"));
    }

    @Test
    public void should_support_configuration_export() throws Exception {
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode yourAttribute = getUnclassifiedRoot(context).get("openTelemetry");

        String exported = toYamlString(yourAttribute);

        String expected = toStringFromYamlFile(this, "configuration-as-code-expected.yml");

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
