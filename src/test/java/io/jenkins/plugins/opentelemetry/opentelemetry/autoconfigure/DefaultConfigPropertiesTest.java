/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class DefaultConfigPropertiesTest {

    @Test
    public void createFromConfiguration() {

        Map<String, String> defaultCfg = new HashMap<>();
        Map<String, String> envVars = new HashMap<>();
        Properties systemProperties = new Properties();
        Map<String, String> overwrites = new HashMap<>();


        // SETUP
        defaultCfg.put("otel.service.name", "jenkins");
        defaultCfg.put("otel.resource.attributes", "service.namespace=jenkins");

        envVars.put("OTEL_EXPORTER_OTLP_ENDPOINT", "https://otel-endpoint.example.com");
        envVars.put("OTEL_EXPORTER_OTLP_HEADERS", "Authorization=Bearer my-token");

        overwrites.put("otel.service.name", "jenkins-123");
        overwrites.put("otel.resource.attributes", "jenkins.url=https://jenkins-123.example.com");


        // VERIFY
        DefaultConfigProperties configProperties = DefaultConfigProperties.createFromConfiguration(overwrites, systemProperties, envVars, defaultCfg);
        MatcherAssert.assertThat(configProperties.getString("otel.service.name"), CoreMatchers.is("jenkins-123"));
        MatcherAssert.assertThat(configProperties.getString("otel.exporter.otlp.endpoint"), CoreMatchers.is("https://otel-endpoint.example.com"));
        final Map<String, String> otlpExporterHeaders = configProperties.getMap("otel.exporter.otlp.headers");
        MatcherAssert.assertThat(otlpExporterHeaders.get("Authorization"), CoreMatchers.is("Bearer my-token"));


        Map<String, String> resourceAttributes = configProperties.getMap("otel.resource.attributes");
        MatcherAssert.assertThat(resourceAttributes.get("service.namespace"), CoreMatchers.is("jenkins"));
        MatcherAssert.assertThat(resourceAttributes.get("jenkins.url"), CoreMatchers.is("https://jenkins-123.example.com"));
    }
}