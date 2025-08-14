/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import groovy.text.GStringTemplateEngine;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class CustomObservabilityBackendTest {

    @Test
    void testGetMetricsVisualizationUrl() {
        CustomObservabilityBackend backend = new CustomObservabilityBackend();
        backend.setMetricsVisualizationUrlTemplate(
                "https://observability.Example.com/dashboards/jenkins?service.name=${resource['service.name']}");
        assertThat("service.name", is(ServiceAttributes.SERVICE_NAME.getKey()));
        Resource resource = Resource.builder()
                .put(ServiceAttributes.SERVICE_NAME, "jenkins")
                .build();

        String actual = backend.getMetricsVisualizationUrl(resource);
        assertThat(
                actual, is("https://observability.Example.com/dashboards/jenkins?service.name=jenkins"));
    }

    @Test
    void testGStringTemplateEngine() throws Exception {
        String template =
                "https://observability.Example.com/dashboards/jenkins?service.name=${resource['service.name']}";

        Map<String, String> resourceAttributesAsMap = Collections.singletonMap("service.name", "jenkins");
        Map<String, Object> binding = Collections.singletonMap("resource", resourceAttributesAsMap);

        String actual = new GStringTemplateEngine()
                .createTemplate(template)
                .make(binding)
                .toString();
        assertThat(
                actual, is("https://observability.Example.com/dashboards/jenkins?service.name=jenkins"));
    }
}
