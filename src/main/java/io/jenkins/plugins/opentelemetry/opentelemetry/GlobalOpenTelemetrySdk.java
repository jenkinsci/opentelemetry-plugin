/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Global singleton similar to the {@link io.opentelemetry.api.GlobalOpenTelemetry} in order to also have a
 * static accessor to the {@link io.opentelemetry.sdk.logs.SdkLoggerProvider}
 * <p>
 * TODO handle reconfiguration
 */
public final class GlobalOpenTelemetrySdk {

    private final static Logger logger = Logger.getLogger(GlobalOpenTelemetrySdk.class.getName());

    private final static ReconfigurableOpenTelemetry openTelemetry;

    private final static io.opentelemetry.api.logs.Logger otelLogger;

    @VisibleForTesting
    static OtelSdkConfiguration currentSdkConfiguration = new OtelSdkConfiguration(Collections.emptyMap(), Collections.emptyMap());

    static {
        openTelemetry = new ReconfigurableOpenTelemetry();
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
        ResourceBuilder resourceBuilder = Resource.builder();
        resourceAttributes.forEach(resourceBuilder::put);
        Resource resource = resourceBuilder.build();

        OtelSdkConfiguration newOtelSdkConfiguration = new OtelSdkConfiguration(configurationProperties, resourceAttributes);
        // VERIFY IF CONFIGURATION HAS CHANGED
        if (Objects.equals(newOtelSdkConfiguration, currentSdkConfiguration)) {
            logger.log(Level.FINEST, () -> "OpenTelemetry SDK configuration has NOT changed, don't reconfigure SDK");
            return;
        }
        logger.log(Level.FINEST, () -> "Configure OpenTelemetry SDK...");
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
