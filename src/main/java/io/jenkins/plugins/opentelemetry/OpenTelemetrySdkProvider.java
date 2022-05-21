/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import hudson.Extension;
import hudson.ExtensionList;
import io.jenkins.plugins.opentelemetry.opentelemetry.GlobalOpenTelemetrySdk;
import io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure.ConfigPropertiesUtils;
import io.jenkins.plugins.opentelemetry.opentelemetry.log.NoopLogEmitter;
import io.jenkins.plugins.opentelemetry.opentelemetry.trace.TracerDelegate;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.extension.resources.ProcessResourceProvider;
import io.opentelemetry.sdk.logs.LogEmitter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;

import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


@Extension
public class OpenTelemetrySdkProvider {

    private static final Logger LOGGER = Logger.getLogger(OpenTelemetrySdkProvider.class.getName());

    /**
     * See {@code OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS}
     */
    public static final String DEFAULT_OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS = ProcessResourceProvider.class.getName();

    protected transient OpenTelemetry openTelemetry;
    protected transient OpenTelemetrySdk openTelemetrySdk;
    protected transient TracerDelegate tracer;
    protected transient Meter meter;
    protected transient Resource resource;
    protected transient ConfigProperties config;
    protected transient LogEmitter logEmitter;

    public OpenTelemetrySdkProvider() {
    }


    @Nonnull
    public Tracer getTracer() {
        return Preconditions.checkNotNull(tracer);
    }

    @Nonnull
    public Meter getMeter() {
        return Preconditions.checkNotNull(meter);
    }

    @Nonnull
    public Resource getResource() {
        return Preconditions.checkNotNull(resource);
    }

    @Nonnull
    public ConfigProperties getConfig() {
        return Preconditions.checkNotNull(config);
    }

    public boolean isOtelLogsEnabled(){
        String otelLogsExporter = config.getString("otel.logs.exporter");
        return otelLogsExporter != null && !otelLogsExporter.equals("none");
    }

    @Nonnull
    public LogEmitter getLogEmitter() {
        return Preconditions.checkNotNull(this.logEmitter);
    }

    @VisibleForTesting
    @Nonnull
    protected OpenTelemetrySdk getOpenTelemetrySdk() {
        return Preconditions.checkNotNull(openTelemetrySdk);
    }

    @PreDestroy
    public void shutdown() {
        if (this.openTelemetrySdk != null) {
            LOGGER.log(Level.INFO, "shutdown...");
            for (OtelComponent otelComponent : ExtensionList.lookup(OtelComponent.class)) {
                LOGGER.log(Level.FINE, () -> "beforeSdkShutdown() " + otelComponent);
                otelComponent.beforeSdkShutdown();
            }
            this.openTelemetrySdk.getSdkTracerProvider().shutdown();
            this.openTelemetrySdk.getSdkMeterProvider().shutdown();
            this.openTelemetrySdk.getSdkLogEmitterProvider().shutdown();
        }
        GlobalOpenTelemetry.resetForTest();
    }

    public void initialize(@Nonnull OpenTelemetryConfiguration configuration) {
        shutdown(); // shutdown existing SDK
        if (configuration.getEndpoint().isPresent()) {
            initializeOtlp(configuration);
        } else {
            initializeNoOp();
        }
        LOGGER.log(Level.INFO, () -> "Initialize Otel SDK on components: " + ExtensionList.lookup(OtelComponent.class).stream().map(e -> e.getClass().getName()).collect(Collectors.joining(", ")));
        for (OtelComponent otelComponent : ExtensionList.lookup(OtelComponent.class)) {
            otelComponent.afterSdkInitialized(meter, logEmitter, tracer, config);
        }
    }

    public void initializeOtlp(@Nonnull OpenTelemetryConfiguration configuration) {

        AutoConfiguredOpenTelemetrySdkBuilder sdkBuilder = AutoConfiguredOpenTelemetrySdk.builder();
        // PROPERTIES
        sdkBuilder.addPropertiesSupplier(() -> configuration.toOpenTelemetryProperties());

        // RESOURCE
        sdkBuilder.addResourceCustomizer((resource, configProperties) -> {
                ResourceBuilder resourceBuilder = Resource.builder()
                    .putAll(resource)
                    .putAll(configuration.toOpenTelemetryResource());
                return resourceBuilder.build();
            }
        );

        sdkBuilder
            .registerShutdownHook(false) // SDK closed by io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider.preDestroy()
            .setResultAsGlobal(true); // ensure GlobalOpenTelemetry.set() is invoked
        AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk = sdkBuilder.build();
        this.openTelemetrySdk = autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk();
        this.resource = autoConfiguredOpenTelemetrySdk.getResource();
        this.config = autoConfiguredOpenTelemetrySdk.getConfig();
        this.openTelemetry = this.openTelemetrySdk;
        Tracer newTracer = openTelemetry.getTracer(GlobalOpenTelemetrySdk.INSTRUMENTATION_NAME, OtelUtils.getOpentelemetryPluginVersion());
        if (tracer == null) {
            this.tracer = new TracerDelegate(newTracer);
        } else {
            this.tracer.setDelegate(newTracer);
        }

        this.logEmitter = openTelemetrySdk.getSdkLogEmitterProvider().get(GlobalOpenTelemetrySdk.INSTRUMENTATION_NAME);
        this.meter = openTelemetry.getMeterProvider().get(GlobalOpenTelemetrySdk.INSTRUMENTATION_NAME);

        LOGGER.log(Level.INFO, () -> "OpenTelemetry SDK initialized: " + OtelUtils.prettyPrintOtelSdkConfig(autoConfiguredOpenTelemetrySdk));
    }

    @VisibleForTesting
    public void initializeNoOp() {
        LOGGER.log(Level.INFO, "initializeNoOp");

        this.openTelemetrySdk = null;
        this.resource = Resource.getDefault();
        this.config = ConfigPropertiesUtils.emptyConfig();
        this.openTelemetry = OpenTelemetry.noop();
        GlobalOpenTelemetry.resetForTest(); // hack for testing in Intellij cause by DiskUsageMonitoringInitializer
        GlobalOpenTelemetry.set(OpenTelemetry.noop());
        if (this.tracer == null) {
            this.tracer = new TracerDelegate(OpenTelemetry.noop().getTracer("noop"));
        } else {
            this.tracer.setDelegate(OpenTelemetry.noop().getTracer("noop"));
        }

        this.meter = OpenTelemetry.noop().getMeter("io.jenkins.opentelemetry");
        this.logEmitter = NoopLogEmitter.noop();
        LOGGER.log(Level.INFO, "OpenTelemetry initialized as NoOp");
    }

    static public OpenTelemetrySdkProvider get() {
        return ExtensionList.lookupSingleton(OpenTelemetrySdkProvider.class);
    }
}
