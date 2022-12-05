/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class GlobalOpenTelemetrySdkTest {

    @Test
    public void testNotSdkConfigured() {
        try {
            GlobalOpenTelemetrySdk.getConfigProperties();
            GlobalOpenTelemetrySdk.getOtelLogger();
            GlobalOpenTelemetrySdk.getMeter();
            GlobalOpenTelemetrySdk.getTracer();
            Resource resource = GlobalOpenTelemetrySdk.getResource();
            assertEquals("Empty Resource: " + resource, 0, resource.getAttributes().size());
        } finally {
            GlobalOpenTelemetrySdk.shutdown();
        }
    }

    @Test
    public void testSdkSetConfigurationOnce() {
        try {
            Map<String, String> config = new HashMap<>();
            config.put("otel.traces.exporter", "none");
            config.put("otel.metrics.exporter", "none");
            config.put("otel.logs.exporter", "none");
            Resource resource = Resource.builder()
                .put(ResourceAttributes.SERVICE_NAME, "jenkins")
                .put(ResourceAttributes.SERVICE_VERSION, "1.2.3")
                .put(ResourceAttributes.SERVICE_NAME.getKey(), "cicd")
                .build();

            Map<String, String> resourceAttributes = new HashMap<>();
            resource.getAttributes().forEach((k, v) -> resourceAttributes.put(k.getKey(), v.toString()));

            int configurationCountBefore = GlobalOpenTelemetrySdk.configurationCounter.get();
            GlobalOpenTelemetrySdk.configure(config, resourceAttributes, false);
            ConfigProperties actualConfigProperties = GlobalOpenTelemetrySdk.getConfigProperties();
            config.forEach((k, v) -> assertEquals("Config[" + k + "]", v, actualConfigProperties.getString(k)));
            GlobalOpenTelemetrySdk.getOtelLogger();
            GlobalOpenTelemetrySdk.getMeter();
            GlobalOpenTelemetrySdk.getTracer();
            Resource actualResource = GlobalOpenTelemetrySdk.getResource();
            resource.getAttributes().forEach((k, v) -> assertEquals("Resource[" + k + "]", v, actualResource.getAttribute(k)));
            assertEquals("Configuration counter", configurationCountBefore + 1, GlobalOpenTelemetrySdk.configurationCounter.get());
        } finally {
            GlobalOpenTelemetrySdk.shutdown();
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
                .put(ResourceAttributes.SERVICE_NAME, "jenkins")
                .put(ResourceAttributes.SERVICE_VERSION, "1.2.3")
                .put(ResourceAttributes.SERVICE_NAME.getKey(), "cicd")
                .build();

            Map<String, String> resourceAttributes = new HashMap<>();
            resource.getAttributes().forEach((k, v) -> resourceAttributes.put(k.getKey(), v.toString()));

            int configurationCountBefore = GlobalOpenTelemetrySdk.configurationCounter.get();

            // CONFIGURE ONCE
            {
                GlobalOpenTelemetrySdk.configure(config, resourceAttributes, false);
                ConfigProperties actualConfigProperties = GlobalOpenTelemetrySdk.getConfigProperties();
                config.forEach((k, v) -> assertEquals("Config[" + k + "]", v, actualConfigProperties.getString(k)));
                GlobalOpenTelemetrySdk.getOtelLogger();
                GlobalOpenTelemetrySdk.getMeter();
                GlobalOpenTelemetrySdk.getTracer();
                Resource actualResource = GlobalOpenTelemetrySdk.getResource();
                resource.getAttributes().forEach((k, v) -> assertEquals("Resource[" + k + "]", v, actualResource.getAttribute(k)));
                assertEquals("Configuration counter", configurationCountBefore + 1, GlobalOpenTelemetrySdk.configurationCounter.get());
            }

            // CONFIGURE A SECOND TIME WITH SAME CONFIGURATION
            {
                GlobalOpenTelemetrySdk.configure(config, resourceAttributes, false);
                ConfigProperties actualConfigProperties = GlobalOpenTelemetrySdk.getConfigProperties();
                config.forEach((k, v) -> assertEquals("Config[" + k + "]", v, actualConfigProperties.getString(k)));
                GlobalOpenTelemetrySdk.getOtelLogger();
                GlobalOpenTelemetrySdk.getMeter();
                GlobalOpenTelemetrySdk.getTracer();
                Resource actualResource = GlobalOpenTelemetrySdk.getResource();
                resource.getAttributes().forEach((k, v) -> assertEquals("Resource[" + k + "]", v, actualResource.getAttribute(k)));
                // verify has been configured just once
                assertEquals("Configuration counter", configurationCountBefore + 1, GlobalOpenTelemetrySdk.configurationCounter.get());
            }
        } finally {
            GlobalOpenTelemetrySdk.shutdown();
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
                    .put(ResourceAttributes.SERVICE_NAME, "jenkins")
                    .put(ResourceAttributes.SERVICE_VERSION, "1.2.3")
                    .put(ResourceAttributes.SERVICE_NAME.getKey(), "cicd")
                    .build();

                final Map<String, String> resourceAttributes = new HashMap<>();
                resource.getAttributes().forEach((k, v) -> resourceAttributes.put(k.getKey(), v.toString()));

                GlobalOpenTelemetrySdk.configure(config, resourceAttributes, false);
                ConfigProperties actualConfigProperties = GlobalOpenTelemetrySdk.getConfigProperties();
                config.forEach((k, v) -> assertEquals("Config[" + k + "]", v, actualConfigProperties.getString(k)));
                GlobalOpenTelemetrySdk.getOtelLogger();
                GlobalOpenTelemetrySdk.getMeter();
                GlobalOpenTelemetrySdk.getTracer();
                Resource actualResource = GlobalOpenTelemetrySdk.getResource();
                resource.getAttributes().forEach((k, v) -> assertEquals("Resource[" + k + "]", v, actualResource.getAttribute(k)));
                assertEquals("Configuration counter", configurationCountBefore + 1, GlobalOpenTelemetrySdk.configurationCounter.get());
            }

            // CONFIGURE A SECOND TIME WITH SAME CONFIG PROPERTIES AND DIFFERENT RESOURCE ATTRIBUTES
            {
                final Resource differentResource = Resource.builder()
                    .put(ResourceAttributes.SERVICE_NAME, "jenkins")
                    .put(ResourceAttributes.SERVICE_VERSION, "1.2.3")
                    .put(ResourceAttributes.SERVICE_NAME.getKey(), "another_namespace")
                    .build();
                final Map<String, String> differentResourceAttributes = new HashMap<>();
                differentResource.getAttributes().forEach((k, v) -> differentResourceAttributes.put(k.getKey(), v.toString()));

                GlobalOpenTelemetrySdk.configure(config, differentResourceAttributes, false);
                ConfigProperties actualConfigProperties = GlobalOpenTelemetrySdk.getConfigProperties();
                config.forEach((k, v) -> assertEquals("Config[" + k + "]", v, actualConfigProperties.getString(k)));
                GlobalOpenTelemetrySdk.getOtelLogger();
                GlobalOpenTelemetrySdk.getMeter();
                GlobalOpenTelemetrySdk.getTracer();
                Resource actualResource = GlobalOpenTelemetrySdk.getResource();
                differentResource.getAttributes().forEach((k, v) -> assertEquals("Resource[" + k + "]", v, actualResource.getAttribute(k)));
                // verify has been configured twice
                assertEquals("Configuration counter", configurationCountBefore + 2, GlobalOpenTelemetrySdk.configurationCounter.get());
            }
        } finally {
            GlobalOpenTelemetrySdk.shutdown();
        }
    }

    @Test
    public void testSdkSetConfiguration_twice_with_different_configProperties_and_same_resourceAttributes() {
        try {


            final Resource resource = Resource.builder()
                .put(ResourceAttributes.SERVICE_NAME, "jenkins")
                .put(ResourceAttributes.SERVICE_VERSION, "1.2.3")
                .put(ResourceAttributes.SERVICE_NAME.getKey(), "cicd")
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
                ConfigProperties actualConfigProperties = GlobalOpenTelemetrySdk.getConfigProperties();
                config.forEach((k, v) -> assertEquals("Config[" + k + "]", v, actualConfigProperties.getString(k)));
                GlobalOpenTelemetrySdk.getOtelLogger();
                GlobalOpenTelemetrySdk.getMeter();
                GlobalOpenTelemetrySdk.getTracer();
                Resource actualResource = GlobalOpenTelemetrySdk.getResource();
                resource.getAttributes().forEach((k, v) -> assertEquals("Resource[" + k + "]", v, actualResource.getAttribute(k)));
                assertEquals("Configuration counter", configurationCountBefore + 1, GlobalOpenTelemetrySdk.configurationCounter.get());
            }

            // CONFIGURE A SECOND TIME WITH DIFFERENT CONFIG PROPERTIES AND SAME RESOURCE ATTRIBUTES
            {
                Map<String, String> differentConfig = new HashMap<>();
                differentConfig.put("otel.traces.exporter", "none");
                differentConfig.put("otel.metrics.exporter", "none");
                differentConfig.put("otel.logs.exporter", "none");
                differentConfig.put("a", "b");

                GlobalOpenTelemetrySdk.configure(differentConfig, resourceAttributes, false);
                ConfigProperties actualConfigProperties = GlobalOpenTelemetrySdk.getConfigProperties();
                differentConfig.forEach((k, v) -> assertEquals("Config[" + k + "]", v, actualConfigProperties.getString(k)));
                GlobalOpenTelemetrySdk.getOtelLogger();
                GlobalOpenTelemetrySdk.getMeter();
                GlobalOpenTelemetrySdk.getTracer();
                Resource actualResource = GlobalOpenTelemetrySdk.getResource();
                resource.getAttributes().forEach((k, v) -> assertEquals("Resource[" + k + "]", v, actualResource.getAttribute(k)));
                // verify has been configured twice
                assertEquals("Configuration counter", configurationCountBefore + 2, GlobalOpenTelemetrySdk.configurationCounter.get());
            }
        } finally {
            GlobalOpenTelemetrySdk.shutdown();
        }
    }
}