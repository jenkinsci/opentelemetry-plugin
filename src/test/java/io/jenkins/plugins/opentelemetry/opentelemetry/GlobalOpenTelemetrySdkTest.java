/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class GlobalOpenTelemetrySdkTest {

    @Test
    public void testNotSdkConfigured() {
        try {
            @SuppressWarnings("unused")
            ReconfigurableOpenTelemetry openTelemetry = GlobalOpenTelemetrySdk.get();
            @SuppressWarnings("unused")
            Logger otelLogger = GlobalOpenTelemetrySdk.getOtelLogger();
        } finally {
            GlobalOpenTelemetrySdk.get().close();
        }
    }

    @Test
    public void testSdkSetConfigurationOnce() {
        GlobalOpenTelemetrySdk.currentSdkConfiguration =
                new GlobalOpenTelemetrySdk.OtelSdkConfiguration(Collections.emptyMap(), Collections.emptyMap());
        try {
            Map<String, String> config = new HashMap<>();
            config.put("otel.traces.exporter", "none");
            config.put("otel.metrics.exporter", "none");
            config.put("otel.logs.exporter", "none");
            Resource resource = Resource.builder()
                    .put(ServiceAttributes.SERVICE_NAME, "jenkins")
                    .put(ServiceAttributes.SERVICE_VERSION, "1.2.3")
                    .put(ServiceIncubatingAttributes.SERVICE_NAMESPACE, "cicd")
                    .build();

            Map<String, String> resourceAttributes = new HashMap<>();
            resource.getAttributes().forEach((k, v) -> resourceAttributes.put(k.getKey(), v.toString()));

            int configurationCountBefore = GlobalOpenTelemetrySdk.configurationCounter.get();
            GlobalOpenTelemetrySdk.configure(config, resourceAttributes, false);
            assertEquals(
                    "Configuration counter",
                    configurationCountBefore + 1,
                    GlobalOpenTelemetrySdk.configurationCounter.get());
        } finally {
            GlobalOpenTelemetrySdk.get().close();
        }
    }

    @Test
    public void testSdkSetConfiguration_twice_with_same_configProperties_and_resourceAttributes() {
        try {
            Map<String, String> config = new HashMap<>();
            config.put("otel.traces.exporter", "none");
            config.put("otel.metrics.exporter", "none");
            config.put("otel.logs.exporter", "none");
            Resource resource = Resource.builder()
                    .put(ServiceAttributes.SERVICE_NAME, "jenkins")
                    .put(ServiceAttributes.SERVICE_VERSION, "1.2.3")
                    .put(ServiceIncubatingAttributes.SERVICE_NAMESPACE, "cicd")
                    .build();

            Map<String, String> resourceAttributes = new HashMap<>();
            resource.getAttributes().forEach((k, v) -> resourceAttributes.put(k.getKey(), v.toString()));

            int configurationCountBefore = GlobalOpenTelemetrySdk.configurationCounter.get();

            // CONFIGURE ONCE
            {
                GlobalOpenTelemetrySdk.configure(config, resourceAttributes, false);
                assertEquals(
                        "Configuration counter",
                        configurationCountBefore + 1,
                        GlobalOpenTelemetrySdk.configurationCounter.get());
            }

            // CONFIGURE A SECOND TIME WITH SAME CONFIGURATION
            {
                GlobalOpenTelemetrySdk.configure(config, resourceAttributes, false);
                // verify has been configured just once
                assertEquals(
                        "Configuration counter",
                        configurationCountBefore + 1,
                        GlobalOpenTelemetrySdk.configurationCounter.get());
            }
        } finally {
            GlobalOpenTelemetrySdk.get().close();
        }
    }

    @Test
    public void testSdkSetConfiguration_twice_with_same_configProperties_and_different_resourceAttributes() {
        try {
            Map<String, String> config = new HashMap<>();
            config.put("otel.traces.exporter", "none");
            config.put("otel.metrics.exporter", "none");
            config.put("otel.logs.exporter", "none");

            int configurationCountBefore = GlobalOpenTelemetrySdk.configurationCounter.get();

            // CONFIGURE ONCE
            {
                final Resource resource = Resource.builder()
                        .put(ServiceAttributes.SERVICE_NAME, "jenkins")
                        .put(ServiceAttributes.SERVICE_VERSION, "1.2.3")
                        .put(ServiceIncubatingAttributes.SERVICE_NAMESPACE, "cicd")
                        .build();

                final Map<String, String> resourceAttributes = new HashMap<>();
                resource.getAttributes().forEach((k, v) -> resourceAttributes.put(k.getKey(), v.toString()));

                GlobalOpenTelemetrySdk.configure(config, resourceAttributes, false);
                assertEquals(
                        "Configuration counter",
                        configurationCountBefore + 1,
                        GlobalOpenTelemetrySdk.configurationCounter.get());
            }

            // CONFIGURE A SECOND TIME WITH SAME CONFIG PROPERTIES AND DIFFERENT RESOURCE ATTRIBUTES
            {
                final Resource differentResource = Resource.builder()
                        .put(ServiceAttributes.SERVICE_NAME, "jenkins")
                        .put(ServiceAttributes.SERVICE_VERSION, "1.2.3")
                        .put(ServiceAttributes.SERVICE_NAME.getKey(), "another_namespace")
                        .build();
                final Map<String, String> differentResourceAttributes = new HashMap<>();
                differentResource
                        .getAttributes()
                        .forEach((k, v) -> differentResourceAttributes.put(k.getKey(), v.toString()));

                GlobalOpenTelemetrySdk.configure(config, differentResourceAttributes, false);
                // verify has been configured twice
                assertEquals(
                        "Configuration counter",
                        configurationCountBefore + 2,
                        GlobalOpenTelemetrySdk.configurationCounter.get());
            }
        } finally {
            GlobalOpenTelemetrySdk.get().close();
        }
    }

    @Test
    public void testSdkSetConfiguration_twice_with_different_configProperties_and_same_resourceAttributes() {
        try {

            final Resource resource = Resource.builder()
                    .put(ServiceAttributes.SERVICE_NAME, "jenkins")
                    .put(ServiceAttributes.SERVICE_VERSION, "1.2.3")
                    .put(ServiceIncubatingAttributes.SERVICE_NAMESPACE, "cicd")
                    .build();

            final Map<String, String> resourceAttributes = new HashMap<>();
            resource.getAttributes().forEach((k, v) -> resourceAttributes.put(k.getKey(), v.toString()));

            int configurationCountBefore = GlobalOpenTelemetrySdk.configurationCounter.get();

            // CONFIGURE ONCE
            {
                Map<String, String> config = new HashMap<>();
                config.put("otel.traces.exporter", "none");
                config.put("otel.metrics.exporter", "none");
                config.put("otel.logs.exporter", "none");

                GlobalOpenTelemetrySdk.configure(config, resourceAttributes, false);
                assertEquals(
                        "Configuration counter",
                        configurationCountBefore + 1,
                        GlobalOpenTelemetrySdk.configurationCounter.get());
            }

            // CONFIGURE A SECOND TIME WITH DIFFERENT CONFIG PROPERTIES AND SAME RESOURCE ATTRIBUTES
            {
                Map<String, String> differentConfig = new HashMap<>();
                differentConfig.put("otel.traces.exporter", "none");
                differentConfig.put("otel.metrics.exporter", "none");
                differentConfig.put("otel.logs.exporter", "none");
                differentConfig.put("a", "b");

                GlobalOpenTelemetrySdk.configure(differentConfig, resourceAttributes, false);
                // verify has been configured twice
                assertEquals(
                        "Configuration counter",
                        configurationCountBefore + 2,
                        GlobalOpenTelemetrySdk.configurationCounter.get());
            }
        } finally {
            GlobalOpenTelemetrySdk.get().close();
        }
    }
}
