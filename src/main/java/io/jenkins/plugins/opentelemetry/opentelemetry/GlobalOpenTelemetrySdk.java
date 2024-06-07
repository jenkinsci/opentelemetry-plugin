/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.incubator.events.GlobalEventLoggerProvider;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import net.jcip.annotations.GuardedBy;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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

    private static final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    /**
     * Used for unit tests
     */
    final static AtomicInteger configurationCounter = new AtomicInteger();

    @GuardedBy("readWriteLock")
    private static volatile OpenTelemetrySdkState openTelemetrySdkState = new NoopOpenTelemetrySdkState();

    /**
     * Configure if configuration has changed
     */
    public static void configure(Map<String, String> configurationProperties, Map<String, String> resourceAttributes, boolean registerShutDownHook) {
        SdkConfigurationParameters sdkConfigurationParameters = new SdkConfigurationParameters(configurationProperties, resourceAttributes);
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            // VERIFY IF CONFIGURATION HAS CHANGED
            if (Objects.equals(sdkConfigurationParameters, openTelemetrySdkState.getSdkConfigurationParameters())) {
                logger.log(Level.FINEST, () -> "OpenTelemetry SDK configuration has NOT changed, don't reinitialize SDK");
                return;
            }
        } finally {
            readLock.unlock();
        }
        Lock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            if (Objects.equals(sdkConfigurationParameters, openTelemetrySdkState.getSdkConfigurationParameters())) {
                logger.log(Level.FINEST, () -> "OpenTelemetry SDK configuration has NOT changed");
                return;
            }
            logger.log(Level.FINEST, () -> "Initialize/reinitialize OpenTelemetry SDK...");
            shutdown();
            AutoConfiguredOpenTelemetrySdkBuilder builder = AutoConfiguredOpenTelemetrySdk.builder()
                .addPropertiesSupplier(sdkConfigurationParameters::getConfigurationProperties)
                .addResourceCustomizer((resource, configProperties) -> {
                    AttributesBuilder attributesBuilder = Attributes.builder();
                    sdkConfigurationParameters.getResourceAttributes().forEach(attributesBuilder::put);
                    Attributes attributes = attributesBuilder.build();

                    return Resource.builder()
                        .putAll(resource)
                        .putAll(attributes)
                        .build();
                });

            if (!registerShutDownHook) {
                builder.disableShutdownHook();
            }
            OpenTelemetrySdk openTelemetrySdk = builder.build().getOpenTelemetrySdk();
            openTelemetrySdkState = new OpenTelemetrySdkStateImpl(openTelemetrySdk, sdkConfigurationParameters);
            logger.log(Level.INFO, () -> "OpenTelemetry SDK initialized"); // TODO dump config details
            configurationCounter.incrementAndGet();
        } finally {
            writeLock.unlock();
        }

    }

    public static CompletableResultCode shutdown() {
        Lock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            CompletableResultCode result = openTelemetrySdkState.shutDown();
            openTelemetrySdkState = new NoopOpenTelemetrySdkState();
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    public static io.opentelemetry.api.logs.Logger getOtelLogger() {
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return openTelemetrySdkState.getOtelLogger();
        } finally {
            readLock.unlock();
        }
    }

    public static Meter getMeter() {
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return openTelemetrySdkState.getMeter();
        } finally {
            readLock.unlock();
        }
    }

    public static Tracer getTracer() {
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return openTelemetrySdkState.getTracer();
        } finally {
            readLock.unlock();
        }
    }

    private interface OpenTelemetrySdkState {
        io.opentelemetry.api.logs.Logger getOtelLogger();

        Meter getMeter();

        Tracer getTracer();

        SdkConfigurationParameters getSdkConfigurationParameters();

        CompletableResultCode shutDown();
    }

    private static class NoopOpenTelemetrySdkState implements OpenTelemetrySdkState {
        @Override
        public io.opentelemetry.api.logs.Logger getOtelLogger() {
            return LoggerProvider.noop().get(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME);
        }

        @Override
        public Meter getMeter() {
            return MeterProvider.noop().get(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME);
        }

        @Override
        public Tracer getTracer() {
            return TracerProvider.noop().get(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME);
        }


        @Override
        public SdkConfigurationParameters getSdkConfigurationParameters() {
            return new SdkConfigurationParameters(Collections.emptyMap(), Collections.emptyMap());
        }

        @Override
        public CompletableResultCode shutDown() {
            return CompletableResultCode.ofSuccess();
        }
    }

    private static class OpenTelemetrySdkStateImpl implements OpenTelemetrySdkState {
        private final OpenTelemetrySdk openTelemetrySdk;

        private final io.opentelemetry.api.logs.Logger otelLogger;
        private final Meter meter;
        private final Tracer tracer;
        private final SdkConfigurationParameters sdkConfigurationParameters;


        OpenTelemetrySdkStateImpl(OpenTelemetrySdk openTelemetrySdk, SdkConfigurationParameters sdkConfigurationParameters) {
            this.openTelemetrySdk = openTelemetrySdk;
            this.sdkConfigurationParameters = sdkConfigurationParameters;
            String jenkinsPluginVersion = Optional.ofNullable(
                sdkConfigurationParameters.resourceAttributes.get(JenkinsOtelSemanticAttributes.JENKINS_OPEN_TELEMETRY_PLUGIN_VERSION.getKey()))
                .orElse("#unknown#");
            this.otelLogger = openTelemetrySdk
                .getSdkLoggerProvider()
                .loggerBuilder(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
                .setInstrumentationVersion(jenkinsPluginVersion)
                .build();
            this.tracer = openTelemetrySdk
                .getTracerProvider().tracerBuilder(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
                .setInstrumentationVersion(jenkinsPluginVersion)
                .build();
            this.meter = openTelemetrySdk
                .getMeterProvider()
                .meterBuilder(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
                .setInstrumentationVersion(jenkinsPluginVersion)
                .build();
        }

        @Override
        public io.opentelemetry.api.logs.Logger getOtelLogger() {
            return otelLogger;
        }

        @Override
        public Meter getMeter() {
            return meter;
        }

        @Override
        public Tracer getTracer() {
            return tracer;
        }

        @Override
        public SdkConfigurationParameters getSdkConfigurationParameters() {
            return sdkConfigurationParameters;
        }

        @Override
        public CompletableResultCode shutDown() {
            logger.log(Level.FINE, "Shutdown OpenTelemetry..."); // TODO dump config details
            CompletableResultCode result = openTelemetrySdk.shutdown();
            GlobalOpenTelemetry.resetForTest();
            GlobalEventLoggerProvider.resetForTest();

            return result;
        }
    }

    static class SdkConfigurationParameters {
        private final Map<String, String> configurationProperties;
        private final Map<String, String> resourceAttributes;

        public SdkConfigurationParameters(Map<String, String> configurationProperties, Map<String, String> resourceAttributes) {
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
            SdkConfigurationParameters that = (SdkConfigurationParameters) o;
            return Objects.equals(configurationProperties, that.configurationProperties) && Objects.equals(resourceAttributes, that.resourceAttributes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(configurationProperties, resourceAttributes);
        }
    }
}
