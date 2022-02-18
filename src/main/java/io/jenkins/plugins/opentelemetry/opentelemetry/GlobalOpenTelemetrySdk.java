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

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
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

    private static final Object mutex = new Object();

    @Nullable
    private static volatile AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk;

    @GuardedBy("mutex")
    @Nullable
    private static String jenkinsPluginVersion;

    @GuardedBy("mutex")
    @Nullable
    private static LogEmitter logEmitter;

    @GuardedBy("mutex")
    @Nullable
    private static Meter meter;

    @GuardedBy("mutex")
    @Nullable
    private static Tracer tracer;

    @GuardedBy("mutex")
    @Nullable
    private static SdkConfigurationParameters sdkConfigurationParameters;

    /**
     * Configure if configuration has changed
     *
     * @param configurationProperties
     * @param resourceAttributes
     */
    public static void configure(Map<String, String> configurationProperties, Map<String, String> resourceAttributes, boolean registerShutDownHook) {
        configure(new SdkConfigurationParameters(configurationProperties, resourceAttributes), registerShutDownHook);
    }

    private static void configure(SdkConfigurationParameters newSdkConfigurationParameters, boolean registerShutDownHook) {
        SdkConfigurationParameters currentSdkConfigurationParameters = GlobalOpenTelemetrySdk.sdkConfigurationParameters;

        if (Objects.equals(newSdkConfigurationParameters, currentSdkConfigurationParameters)) {
            logger.log(Level.FINEST, () -> "OpenTelemetry SDK configuration has NOT changed, don't reinitialize SDK");
        } else {
            synchronized (mutex) {
                currentSdkConfigurationParameters = GlobalOpenTelemetrySdk.sdkConfigurationParameters;
                if (Objects.equals(newSdkConfigurationParameters, currentSdkConfigurationParameters)) {
                    logger.log(Level.FINEST, () -> "OpenTelemetry SDK configuration has NOT changed");
                } else {
                    logger.log(Level.FINEST, () -> "Initialize/reinitialize OpenTelemetry SDK...");
                    shutdown();
                    autoConfiguredOpenTelemetrySdk = AutoConfiguredOpenTelemetrySdk.builder()
                        .addPropertiesSupplier(() -> newSdkConfigurationParameters.configurationProperties)
                        .addResourceCustomizer((resource, configProperties) -> {
                            AttributesBuilder builder = Attributes.builder();
                            newSdkConfigurationParameters.resourceAttributes.forEach(builder::put);
                            return Resource.builder()
                                .putAll(resource)
                                .putAll(builder.build())
                                .build();
                        })
                        .registerShutdownHook(registerShutDownHook)
                        .build();
                    logger.log(Level.INFO, () -> "OpenTelemetry SDK initialized: " + OtelUtils.prettyPrintOtelSdkConfig(autoConfiguredOpenTelemetrySdk));
                    jenkinsPluginVersion = Objects.toString(autoConfiguredOpenTelemetrySdk.getResource().getAttribute(JenkinsOtelSemanticAttributes.JENKINS_OPEN_TELEMETRY_PLUGIN_VERSION), "unknown");
                    logEmitter = autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getSdkLogEmitterProvider().get(INSTRUMENTATION_NAME);
                    tracer = autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getTracerProvider().get(INSTRUMENTATION_NAME, jenkinsPluginVersion);
                    meter = autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getMeter(INSTRUMENTATION_NAME);
                    sdkConfigurationParameters = newSdkConfigurationParameters;
                }
            }
        }
    }

    public static CompletableResultCode shutdown() {
        AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk = GlobalOpenTelemetrySdk.autoConfiguredOpenTelemetrySdk;
        CompletableResultCode result;
        if (autoConfiguredOpenTelemetrySdk == null) {
            result = CompletableResultCode.ofSuccess();
        } else {
            synchronized (mutex) {
                autoConfiguredOpenTelemetrySdk = GlobalOpenTelemetrySdk.autoConfiguredOpenTelemetrySdk;
                if (autoConfiguredOpenTelemetrySdk == null) {
                    result = CompletableResultCode.ofSuccess();
                } else {
                    logger.log(Level.INFO, () -> "Shutdown OpenTelemetry " +
                        OtelUtils.prettyPrintOtelSdkConfig(GlobalOpenTelemetrySdk.autoConfiguredOpenTelemetrySdk) +
                        "...");

                    result = CompletableResultCode.ofAll(Arrays.asList(
                        autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getSdkTracerProvider().shutdown(),
                        autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getSdkMeterProvider().shutdown(),
                        autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getSdkLogEmitterProvider().shutdown()));

                    GlobalOpenTelemetrySdk.autoConfiguredOpenTelemetrySdk = null;
                    GlobalOpenTelemetrySdk.sdkConfigurationParameters = null;
                    GlobalOpenTelemetrySdk.jenkinsPluginVersion = null;
                    GlobalOpenTelemetrySdk.tracer = null;
                    GlobalOpenTelemetrySdk.meter = null;
                    GlobalOpenTelemetrySdk.logEmitter = null;
                    GlobalOpenTelemetry.resetForTest();
                }
            }
        }
        return result;
    }

    public static LogEmitter getLogEmitter() {
        if (logEmitter == null) {
            synchronized (mutex) {
                if (logEmitter == null) {
                    logEmitter = NoopLogEmitter.noop();
                }
            }
        }
        return logEmitter;
    }

    public static Meter getMeter() {
        if (meter == null) {
            synchronized (mutex) {
                if (meter == null) {
                    meter = MeterProvider.noop().get(INSTRUMENTATION_NAME);
                }
            }
        }
        return meter;
    }

    public static Tracer getTracer() {
        if (tracer == null) {
            synchronized (mutex) {
                if (tracer == null) {
                    tracer = TracerProvider.noop().get(INSTRUMENTATION_NAME);
                }
            }
        }
        return tracer;
    }

    /**
     * Returns the {@link Resource} of the configured {@link io.opentelemetry.sdk.OpenTelemetrySdk}
     * or an empty {@link Resource} if the sdk is not configured and noop.
     */
    public static Resource getResource() {
        AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk = GlobalOpenTelemetrySdk.autoConfiguredOpenTelemetrySdk;
        if (autoConfiguredOpenTelemetrySdk == null) {
            return Resource.empty();
        } else {
            return autoConfiguredOpenTelemetrySdk.getResource();
        }
    }

    /**
     * Returns the {@link ConfigProperties} of the configured {@link io.opentelemetry.sdk.OpenTelemetrySdk}
     * or an empty {@link ConfigProperties} if the sdk is not configured and noop.
     */
    public static ConfigProperties getConfigProperties() {
        AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk = GlobalOpenTelemetrySdk.autoConfiguredOpenTelemetrySdk;
        if (autoConfiguredOpenTelemetrySdk == null) {
            return ConfigPropertiesUtils.emptyConfig();
        } else {
            return autoConfiguredOpenTelemetrySdk.getConfig();
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
