/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.instrumentation.resources.ContainerResourceProvider;
import io.opentelemetry.instrumentation.resources.HostIdResourceProvider;
import io.opentelemetry.instrumentation.resources.HostResourceProvider;
import io.opentelemetry.instrumentation.resources.OsResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Global singleton similar to the {@link io.opentelemetry.api.GlobalOpenTelemetry} in order to also have a
 * static accessor to the {@link io.opentelemetry.sdk.logs.SdkLoggerProvider}
 */
public final class GlobalOpenTelemetrySdk {

    private final static Logger logger = Logger.getLogger(GlobalOpenTelemetrySdk.class.getName());

    private final static ReconfigurableOpenTelemetry openTelemetry;

    private final static io.opentelemetry.api.logs.Logger otelLogger;

    @VisibleForTesting
    static OtelSdkConfiguration currentSdkConfiguration = new OtelSdkConfiguration(Collections.emptyMap(), Collections.emptyMap());

    static {
        openTelemetry = ReconfigurableOpenTelemetry.get();
        otelLogger = openTelemetry
            .getLogsBridge()
            .loggerBuilder(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
            .build();
    }

    /**
     * Used for unit tests
     */
    final static AtomicInteger configurationCounter = new AtomicInteger();

    @Nonnull
    public static ReconfigurableOpenTelemetry get() {
        return GlobalOpenTelemetrySdk.openTelemetry;
    }

    public static boolean isInitialized() {
        return GlobalOpenTelemetrySdk.openTelemetry != null;
    }

    @Nonnull
    public static io.opentelemetry.api.logs.Logger getOtelLogger() {
        return otelLogger;
    }

    /**
     * Configure if configuration has changed
     */
    public static synchronized void configure(Map<String, String> configurationProperties, Map<String, String> resourceAttributes, boolean registerShutDownHook) {
        OtelSdkConfiguration newOtelSdkConfiguration = new OtelSdkConfiguration(configurationProperties, resourceAttributes);
        // VERIFY IF CONFIGURATION HAS CHANGED
        if (Objects.equals(newOtelSdkConfiguration, currentSdkConfiguration)) {
            logger.log(Level.FINEST, () -> "OpenTelemetry SDK configuration has NOT changed, don't reconfigure SDK");
            return;
        }
        logger.log(Level.FINEST, () -> "Configure OpenTelemetry SDK...");

        ConfigProperties configProperties = DefaultConfigProperties.create(configurationProperties);
        ResourceBuilder resourceBuilder = Resource.builder();
        resourceBuilder.putAll(new HostResourceProvider().createResource(configProperties));
        resourceBuilder.putAll(new HostIdResourceProvider().createResource(configProperties));
        resourceBuilder.putAll(new ContainerResourceProvider().createResource(configProperties));
        // resourceBuilder.putAll(new ProcessResourceProvider().createResource(configProperties));
        resourceBuilder.putAll(new OsResourceProvider().createResource(configProperties));
        resourceAttributes
            .entrySet().stream()
            .filter(Predicate.not(entry -> Objects.equals(ServiceIncubatingAttributes.SERVICE_INSTANCE_ID.getKey(), entry.getKey())))
            .forEach(entry -> resourceBuilder.put(entry.getKey(), entry.getValue()));
        Resource resource = resourceBuilder.build();

        get().configure(configurationProperties, resource, registerShutDownHook);
        currentSdkConfiguration = newOtelSdkConfiguration;
        configurationCounter.incrementAndGet();
    }

    static class OtelSdkConfiguration {
        private final Map<String, String> configurationProperties;
        private final Map<String, String> resourceAttributes;

        public OtelSdkConfiguration(Map<String, String> configurationProperties, Map<String, String> resourceAttributes) {
            this.configurationProperties = configurationProperties;
            this.resourceAttributes = resourceAttributes;
        }

        public Map<String, String> getConfigurationProperties() {
            return configurationProperties;
        }

        public Map<String, String> getResourceAttributes() {
            return resourceAttributes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OtelSdkConfiguration that = (OtelSdkConfiguration) o;
            return Objects.equals(configurationProperties, that.configurationProperties) && Objects.equals(resourceAttributes, that.resourceAttributes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(configurationProperties, resourceAttributes);
        }
    }
}
