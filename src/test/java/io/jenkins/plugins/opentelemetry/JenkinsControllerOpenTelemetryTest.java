/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class JenkinsControllerOpenTelemetryTest {

    @Test
    void testOverwriteDefaultConfig() {
        String serviceNameDefinedInConfig = null;
        String serviceNamespaceDefinedInConfig = null;
        String expectedServiceName = "jenkins";
        String expectedServiceNamespace = "jenkins";

        testDefaultConfigurationOverwrite(
                serviceNameDefinedInConfig,
                serviceNamespaceDefinedInConfig,
                expectedServiceName,
                expectedServiceNamespace);
    }

    @Test
    void testDefaultConfig() {
        String serviceNameDefinedInConfig = "my-jenkins";
        String serviceNamespaceDefinedInConfig = "my-namespace";
        String expectedServiceName = "my-jenkins";
        String expectedServiceNamespace = "my-namespace";

        System.out.println(getClass().getName() + "#testDefaultConfig: systemProperty[jenkins.url]: "
                + System.getProperty("jenkins.url") + ", env[JENKINS_URL]: " + System.getenv("JENKINS_URL"));
        testDefaultConfigurationOverwrite(
                serviceNameDefinedInConfig,
                serviceNamespaceDefinedInConfig,
                expectedServiceName,
                expectedServiceNamespace);
    }

    private void testDefaultConfigurationOverwrite(
            String serviceNameDefinedInConfig,
            String serviceNamespaceDefinedInConfig,
            String expectedServiceName,
            String expectedServiceNamespace) {
        Map<String, String> configurationProperties = new HashMap<>();
        configurationProperties.put("jenkins.version", "1.2.3");
        configurationProperties.put("jenkins.url", "https://jenkins.example.com/");

        OpenTelemetryConfiguration openTelemetryConfiguration = new OpenTelemetryConfiguration(
                Optional.of("http://localhost:4317/"),
                Optional.empty(),
                Optional.empty(),
                Optional.ofNullable(serviceNameDefinedInConfig),
                Optional.ofNullable(serviceNamespaceDefinedInConfig),
                Optional.empty(),
                configurationProperties);

        ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = ReconfigurableOpenTelemetry.get();
        JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry = new JenkinsControllerOpenTelemetry();
        jenkinsControllerOpenTelemetry.openTelemetry = reconfigurableOpenTelemetry;
        jenkinsControllerOpenTelemetry.initialize(openTelemetryConfiguration);

        Resource resource = reconfigurableOpenTelemetry.getResource();
        // resource.getAttributes().forEach((key, value)-> System.out.println(key + ": " + value));

        assertThat(
                resource.getAttribute(ServiceAttributes.SERVICE_NAME), is(expectedServiceName));
        assertThat(
                resource.getAttribute(ServiceIncubatingAttributes.SERVICE_NAMESPACE),
                is(expectedServiceNamespace));
        if (System.getenv("JENKINS_URL") == null && System.getProperty("jenkins.url") == null) {
            assertThat(
                    resource.getAttribute(ExtendedJenkinsAttributes.JENKINS_URL),
                    is("https://jenkins.example.com/"));
        } else {
            // on ci.jenkins.io, the JENKINS_URL environment variable is set to 'https://ci.jenkins.io", breaking the
            // check
            System.out.println(getClass().getName()
                    + "#testDefaultConfigurationOverwrite: skip verification of Resource['jenkins.url'] "
                    + "because the environment variable or the system property is specified");
        }
        assertThat(
                resource.getAttribute(ExtendedJenkinsAttributes.JENKINS_VERSION), is("1.2.3"));
        assertThat(resource.getAttribute(ServiceAttributes.SERVICE_VERSION), is("1.2.3"));

        reconfigurableOpenTelemetry.close();
    }
}
