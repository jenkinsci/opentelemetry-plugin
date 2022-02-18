/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure.ConfigPropertiesUtils;
import io.jenkins.plugins.opentelemetry.opentelemetry.log.NoopLogEmitter;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogEmitter;
import io.opentelemetry.sdk.resources.Resource;
import net.jcip.annotations.GuardedBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Global singleton similar to the {@link io.opentelemetry.api.GlobalOpenTelemetry} in order to also have a
 * static accessor to the {@link io.opentelemetry.sdk.logs.SdkLogEmitterProvider}
 * <p>
 * TODO handle reconfiguration
 */
public final class GlobalOpenTelemetrySdk {
    public final static String INSTRUMENTATION_NAME = "io.jenkins.opentelemetry";

    private final static Logger logger = Logger.getLogger(GlobalOpenTelemetrySdk.class.getName());

    private static final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    /** Used for unit tests */
    final static AtomicInteger configurationCounter = new AtomicInteger();

    @GuardedBy("readWriteLock")
    private static volatile OpenTelemetrySdkState openTelemetrySdkState = new NoopOpenTelemetrySdkState();

    /**
     * Configure if configuration has changed
     */
    public static void configure(Map<String, String> configurationProperties, Map<String, String> resourceAttributes, boolean registerShutDownHook) {
        configure(new SdkConfigurationParameters(configurationProperties, resourceAttributes), registerShutDownHook);
    }

    private static void configure(SdkConfigurationParameters newSdkConfigurationParameters, boolean registerShutDownHook) {
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            // VERIFY IF CONFIGURATION HAS CHANGED
            if (Objects.equals(newSdkConfigurationParameters, openTelemetrySdkState.getSdkConfigurationParameters())) {
                logger.log(Level.FINEST, () -> "OpenTelemetry SDK configuration has NOT changed, don't reinitialize SDK");
                return;
            }
        } finally {
            readLock.unlock();
        }
        Lock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            if (Objects.equals(newSdkConfigurationParameters, openTelemetrySdkState.getSdkConfigurationParameters())) {
                logger.log(Level.FINEST, () -> "OpenTelemetry SDK configuration has NOT changed");
                return;
            }
            logger.log(Level.FINEST, () -> "Initialize/reinitialize OpenTelemetry SDK...");
            shutdown();
            final AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk = AutoConfiguredOpenTelemetrySdk.builder()
                .addPropertiesSupplier(() -> newSdkConfigurationParameters.configurationProperties)
                .addResourceCustomizer((resource, configProperties) -> {
                    AttributesBuilder attributesBuilder = Attributes.builder();
                    newSdkConfigurationParameters.resourceAttributes.forEach(attributesBuilder::put);
                    return Resource.builder()
                        .putAll(resource)
                        .putAll(attributesBuilder.build())
                        .build();
                })
                .registerShutdownHook(registerShutDownHook)
                .build();
            openTelemetrySdkState = new OpenTelemetrySdkStateImpl(autoConfiguredOpenTelemetrySdk, newSdkConfigurationParameters);
            logger.log(Level.INFO, () -> "OpenTelemetry SDK initialized: " + OtelUtils.prettyPrintOtelSdkConfig(autoConfiguredOpenTelemetrySdk));
            configurationCounter.incrementAndGet();
        } finally {
            writeLock.unlock();
        }

    }

    public static CompletableResultCode shutdown() {
        Lock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            final CompletableResultCode result = openTelemetrySdkState.shutDown();
            openTelemetrySdkState = new NoopOpenTelemetrySdkState();
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    public static LogEmitter getLogEmitter() {
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return openTelemetrySdkState.getLogEmitter();
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

    /**
     * Returns the {@link Resource} of the configured {@link io.opentelemetry.sdk.OpenTelemetrySdk}
     * or an empty {@link Resource} if the sdk is not configured and noop.
     */
    public static Resource getResource() {
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return openTelemetrySdkState.getResource();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns the {@link ConfigProperties} of the configured {@link io.opentelemetry.sdk.OpenTelemetrySdk}
     * or an empty {@link ConfigProperties} if the sdk is not configured and noop.
     */
    public static ConfigProperties getConfigProperties() {
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return openTelemetrySdkState.getConfig();
        } finally {
            readLock.unlock();
        }
    }


    private interface OpenTelemetrySdkState {
        LogEmitter getLogEmitter();

        Meter getMeter();

        Tracer getTracer();

        Resource getResource();

        ConfigProperties getConfig();

        SdkConfigurationParameters getSdkConfigurationParameters();

        CompletableResultCode shutDown();
    }

    private static class NoopOpenTelemetrySdkState implements OpenTelemetrySdkState {
        @Override
        public LogEmitter getLogEmitter() {
            return NoopLogEmitter.noop();
        }

        @Override
        public Meter getMeter() {
            return MeterProvider.noop().get(INSTRUMENTATION_NAME);
        }

        @Override
        public Tracer getTracer() {
            return TracerProvider.noop().get(INSTRUMENTATION_NAME);
        }

        @Override
        public Resource getResource() {
            return Resource.empty();
        }

        @Override
        public ConfigProperties getConfig() {
            return ConfigPropertiesUtils.emptyConfig();
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
        private final AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk;
        private final LogEmitter logEmitter;
        private final Meter meter;
        private final Tracer tracer;
        private final SdkConfigurationParameters sdkConfigurationParameters;

        OpenTelemetrySdkStateImpl(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk, SdkConfigurationParameters sdkConfigurationParameters) {
            this.autoConfiguredOpenTelemetrySdk = autoConfiguredOpenTelemetrySdk;
            this.sdkConfigurationParameters = sdkConfigurationParameters;
            String jenkinsPluginVersion = Objects.toString(autoConfiguredOpenTelemetrySdk.getResource().getAttribute(JenkinsOtelSemanticAttributes.JENKINS_OPEN_TELEMETRY_PLUGIN_VERSION), "#unknown#");
            this.logEmitter = autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getSdkLogEmitterProvider().get(INSTRUMENTATION_NAME);
            this.tracer = autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getTracerProvider().get(INSTRUMENTATION_NAME, jenkinsPluginVersion);
            this.meter = autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getMeter(INSTRUMENTATION_NAME);
        }

        @Override
        public LogEmitter getLogEmitter() {
            return logEmitter;
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
        public Resource getResource() {
            return autoConfiguredOpenTelemetrySdk.getResource();
        }

        @Override
        public ConfigProperties getConfig() {
            return autoConfiguredOpenTelemetrySdk.getConfig();
        }

        @Override
        public SdkConfigurationParameters getSdkConfigurationParameters() {
            return sdkConfigurationParameters;
        }

        @Override
        public CompletableResultCode shutDown() {
            logger.log(Level.INFO, "Shutdown OpenTelemetry " + OtelUtils.prettyPrintOtelSdkConfig(autoConfiguredOpenTelemetrySdk) + "...");
            final CompletableResultCode result = CompletableResultCode.ofAll(Arrays.asList(
                autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getSdkTracerProvider().shutdown(),
                autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getSdkMeterProvider().shutdown(),
                autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getSdkLogEmitterProvider().shutdown()));
            GlobalOpenTelemetry.resetForTest();

            return result;
        }
    }

    static class SdkConfigurationParameters {
        final Map<String, String> configurationProperties;
        final Map<String, String> resourceAttributes;

        public SdkConfigurationParameters(Map<String, String> configurationProperties, Map<String, String> resourceAttributes) {
            this.configurationProperties = configurationProperties;
            this.resourceAttributes = resourceAttributes;
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
