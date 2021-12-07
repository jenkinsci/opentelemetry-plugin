/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import groovy.text.GStringTemplateEngine;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class CustomObservabilityBackendTest {

    @Test
    public void testGetMetricsVisualizationUrl() {
        CustomObservabilityBackend backend = new CustomObservabilityBackend();
        backend.setMetricsVisualizationUrlTemplate("https://observability.Example.com/dashboards/jenkins?service.name=${resource['service.name']}");
        MatcherAssert.assertThat("service.name", CoreMatchers.is(ResourceAttributes.SERVICE_NAME.getKey()));
        Resource resource = Resource.builder().put(ResourceAttributes.SERVICE_NAME, "jenkins").build();

        String actual = backend.getMetricsVisualizationUrl(resource);
        MatcherAssert.assertThat(actual, CoreMatchers.is("https://observability.Example.com/dashboards/jenkins?service.name=jenkins"));
    }

    @Test
    public void testGStringTemplateEngine() throws IOException, ClassNotFoundException {
        String template = "https://observability.Example.com/dashboards/jenkins?service.name=${resource['service.name']}";

        Map<String, String> resourceAttributesAsMap = Collections.singletonMap("service.name", "jenkins");
        Map<String, Object> binding = Collections.singletonMap("resource", resourceAttributesAsMap);

        String actual = new GStringTemplateEngine().createTemplate(template).make(binding).toString();
        MatcherAssert.assertThat(actual, CoreMatchers.is("https://observability.Example.com/dashboards/jenkins?service.name=jenkins"));
    }

}