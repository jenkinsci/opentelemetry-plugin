/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class OpenTelemetrySdkProviderTest {

    @Test
    public void testOverwriteDefaultConfig() {
        String serviceNameDefinedInConfig = null;
        String serviceNamespaceDefinedInConfig = null;
        String expectedServiceName = "jenkins";
        String expectedServiceNamespace = "jenkins";

        testDefaultConfigurationOverwrite(serviceNameDefinedInConfig, serviceNamespaceDefinedInConfig, expectedServiceName, expectedServiceNamespace);
    }

    @Test
    public void testDefaultConfig() {
        String serviceNameDefinedInConfig = "my-jenkins";
        String serviceNamespaceDefinedInConfig = "my-namespace";
        String expectedServiceName = "my-jenkins";
        String expectedServiceNamespace = "my-namespace";

        System.out.println(getClass().getName() + "#testDefaultConfig: systemProperty[jenkins.url]: " + System.getProperty("jenkins.url") + ", env[JENKINS_URL]: " + System.getenv("JENKINS_URL"));
        testDefaultConfigurationOverwrite(serviceNameDefinedInConfig, serviceNamespaceDefinedInConfig, expectedServiceName, expectedServiceNamespace);
    }

    private void testDefaultConfigurationOverwrite(String serviceNameDefinedInConfig, String serviceNamespaceDefinedInConfig, String expectedServiceName, String expectedServiceNamespace) {
        Map<String, String> configurationProperties = new HashMap<>();
        configurationProperties.put("jenkins.version", "1.2.3");
        configurationProperties.put("jenkins.url", "https://jenkins.example.com/");

        OpenTelemetryConfiguration openTelemetryConfiguration = new OpenTelemetryConfiguration(
            Optional.of("http://localhost:4317/"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.ofNullable(serviceNameDefinedInConfig),
            Optional.ofNullable(serviceNamespaceDefinedInConfig),
            Optional.empty(),
            configurationProperties);

        OpenTelemetrySdkProvider openTelemetrySdkProvider = new OpenTelemetrySdkProvider();
        openTelemetrySdkProvider.postConstruct();
        openTelemetrySdkProvider.initialize(openTelemetryConfiguration);

        Resource resource = openTelemetrySdkProvider.getResource();
        // resource.getAttributes().forEach((key, value)-> System.out.println(key + ": " + value));

        MatcherAssert.assertThat(
            resource.getAttribute(ResourceAttributes.SERVICE_NAME),
            CoreMatchers.is(expectedServiceName));
        MatcherAssert.assertThat(
            resource.getAttribute(ResourceAttributes.SERVICE_NAMESPACE),
            CoreMatchers.is(expectedServiceNamespace));
        if (System.getenv("JENKINS_URL") == null && System.getProperty("jenkins.url") == null) {
            MatcherAssert.assertThat(
                resource.getAttribute(JenkinsOtelSemanticAttributes.JENKINS_URL),
                CoreMatchers.is("https://jenkins.example.com/"));
        } else {
            // on ci.jenkins.io, the JENKINS_URL environment variable is set to 'https://ci.jenkins.io", breaking the check
            System.out.println(getClass().getName() + "#testDefaultConfigurationOverwrite: skip verification of Resource['jenkins.url'] " +
                "because the environment variable or the system property is specified");
        }
        MatcherAssert.assertThat(
            resource.getAttribute(JenkinsOtelSemanticAttributes.JENKINS_VERSION),
            CoreMatchers.is("1.2.3"));
        MatcherAssert.assertThat(
            resource.getAttribute(ResourceAttributes.SERVICE_VERSION),
            CoreMatchers.is("1.2.3"));


        openTelemetrySdkProvider.preDestroy();
    }
}
