/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import jenkins.model.JenkinsLocationConfiguration;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

import java.util.Optional;

public class OpenTelemetrySdkProviderTest {

    @Test
    public void testOverwriteDefaultConfig(){
        String serviceNameDefinedInConfig = null;
        String serviceNamespaceDefinedInConfig = null;
        String expectedServiceName = "jenkins";
        String expectedServiceNamespace = "jenkins";

        testDefaultConfigurationOverwrite(serviceNameDefinedInConfig, serviceNamespaceDefinedInConfig, expectedServiceName, expectedServiceNamespace);
    }

    @Test
    public void testDefaultConfig(){
        String serviceNameDefinedInConfig = "my-jenkins";
        String serviceNamespaceDefinedInConfig = "my-namespace";
        String expectedServiceName = "my-jenkins";
        String expectedServiceNamespace = "my-namespace";

        testDefaultConfigurationOverwrite(serviceNameDefinedInConfig, serviceNamespaceDefinedInConfig, expectedServiceName, expectedServiceNamespace);
    }

    private void testDefaultConfigurationOverwrite(String serviceNameDefinedInConfig, String serviceNamespaceDefinedInConfig, String expectedServiceName, String expectedServiceNamespace) {
        JenkinsLocationConfiguration jenkinsLocationConfiguration =  new JenkinsLocationConfiguration(){
            @Override
            public String getUrl() {
                return "https://jenkins.example.com/";
            }
        };
        OpenTelemetryConfiguration openTelemetryConfiguration = new OpenTelemetryConfiguration(
            Optional.of("http://localhost:4317/"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.ofNullable(serviceNameDefinedInConfig),
            Optional.ofNullable(serviceNamespaceDefinedInConfig),
            Optional.empty());

        OpenTelemetrySdkProvider openTelemetrySdkProvider = new OpenTelemetrySdkProvider();
        openTelemetrySdkProvider.setJenkinsLocationConfiguration(jenkinsLocationConfiguration);
        openTelemetrySdkProvider.postConstruct();
        openTelemetrySdkProvider.initialize(openTelemetryConfiguration);

        Resource resource = openTelemetrySdkProvider.getResource();
        // resource.getAttributes().forEach((key, value)-> System.out.println(key + ": " + value));

        String actualServiceName = resource.getAttribute(ResourceAttributes.SERVICE_NAME);
        String actualServiceNamespace = resource.getAttribute(ResourceAttributes.SERVICE_NAMESPACE);
        MatcherAssert.assertThat(actualServiceName, CoreMatchers.is(expectedServiceName));
        MatcherAssert.assertThat(actualServiceNamespace, CoreMatchers.is(expectedServiceNamespace));

        openTelemetrySdkProvider.preDestroy();
    }
}