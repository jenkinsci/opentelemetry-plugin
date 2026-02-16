/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import org.junit.jupiter.api.Test;

public class DynatraceBackendTest {

    @Test
    public void testGetMetricsVisualizationUrlDashboardIsSet() {
        DynatraceBackend backend = new DynatraceBackend("https://{your-environment-id}.live.dynatrace.com/");
        backend.setDashboardId("311fa105-1f09-4005-926d-8d27bc33a717");
        Resource resource = Resource.builder()
                .put(ServiceAttributes.SERVICE_NAME, "jenkins")
                .build();

        String actual = backend.getMetricsVisualizationUrl(resource);
        assertThat(
                actual,
                is(
                        "https://{your-environment-id}.live.dynatrace.com/#dashboard;id=311fa105-1f09-4005-926d-8d27bc33a717;gf=all;gtf=today"));
    }

    @Test
    public void testGetMetricsVisualizationUrlDashboardIsNotSet() {
        DynatraceBackend backend = new DynatraceBackend("https://{your-environment-id}.live.dynatrace.com/");
        Resource resource = Resource.builder()
                .put(ServiceAttributes.SERVICE_NAME, "jenkins")
                .build();

        String actual = backend.getMetricsVisualizationUrl(resource);
        assertThat(actual, is(nullValue()));
    }
}
