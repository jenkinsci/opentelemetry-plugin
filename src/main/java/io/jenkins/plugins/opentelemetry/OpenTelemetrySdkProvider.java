/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import hudson.Extension;
import hudson.ExtensionList;
import io.jenkins.plugins.opentelemetry.opentelemetry.ClosingOpenTelemetry;
import io.jenkins.plugins.opentelemetry.opentelemetry.GlobalOpenTelemetrySdk;
import io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure.ConfigPropertiesUtils;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.events.EventEmitterProvider;
import io.opentelemetry.api.events.GlobalEventEmitterProvider;
import io.opentelemetry.api.logs.GlobalLoggerProvider;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.resources.ProcessResourceProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
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

    protected transient ClosingOpenTelemetry openTelemetry;
    protected transient OpenTelemetrySdk openTelemetrySdk;
    @Nonnull
    protected final transient TracerDelegate tracer = new TracerDelegate();
    protected transient Meter meter;
    protected transient Resource resource;
    protected transient ConfigProperties config;
    protected transient LoggerProvider loggerProvider;
    protected transient EventEmitter eventEmitter;

    public OpenTelemetrySdkProvider() {
    }


    @Nonnull
    public Tracer getTracer() {
        return Preconditions.checkNotNull(tracer.getDelegate());
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

    public boolean isOtelLogsMirrorToDisk(){
        String otelLogsExporter = config.getString("otel.logs.mirror_to_disk");
        return otelLogsExporter != null && otelLogsExporter.equals("true");
    }

    @Nonnull
    public LoggerProvider getLoggerProvider() {
        return Preconditions.checkNotNull(this.loggerProvider);
    }

    @Nonnull
    public EventEmitter getEventEmitter() {
        return Preconditions.checkNotNull(this.eventEmitter);
    }

    @VisibleForTesting
    @Nonnull
    protected OpenTelemetrySdk getOpenTelemetrySdk() {
        return Preconditions.checkNotNull(openTelemetrySdk);
    }

    @PreDestroy
    public void shutdown() {
        if (this.openTelemetrySdk != null) {
            LOGGER.log(Level.FINE, "Shutdown...");
            LOGGER.log(Level.FINE, () -> "Shutdown Otel SDK on components: " + ExtensionList.lookup(OtelComponent.class).stream().sorted().map(e -> e.getClass().getName()).collect(Collectors.joining(", ")));

            ExtensionList.lookup(OtelComponent.class).stream().sorted().forEachOrdered(OtelComponent::beforeSdkShutdown);
            this.openTelemetry.close();
            this.openTelemetrySdk.getSdkTracerProvider().shutdown();
            this.openTelemetrySdk.getSdkMeterProvider().shutdown();
            this.openTelemetrySdk.getSdkLoggerProvider().shutdown();
        }
        GlobalOpenTelemetry.resetForTest();
        GlobalLoggerProvider.resetForTest();
        GlobalEventEmitterProvider.resetForTest();
    }

    public void initialize(@Nonnull OpenTelemetryConfiguration configuration) {
        shutdown(); // shutdown existing SDK
        if (configuration.getEndpoint().isPresent()) {
            initializeOtlp(configuration);
        } else {
            initializeNoOp();
        }
        LOGGER.log(Level.FINE, () -> "Initialize Otel SDK on components: " + ExtensionList.lookup(OtelComponent.class).stream().sorted().map(e -> e.getClass().getName()).collect(Collectors.joining(", ")));
        ExtensionList.lookup(OtelComponent.class).stream().sorted().forEachOrdered(otelComponent -> {
            otelComponent.afterSdkInitialized(meter, loggerProvider, eventEmitter, tracer, config);
            otelComponent.afterSdkInitialized(openTelemetry, config);
        });
    }

    public void initializeOtlp(@Nonnull OpenTelemetryConfiguration configuration) {

        AutoConfiguredOpenTelemetrySdkBuilder sdkBuilder = AutoConfiguredOpenTelemetrySdk.builder();
        // PROPERTIES
        sdkBuilder.addPropertiesSupplier(configuration::toOpenTelemetryProperties);

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
        this.openTelemetry = new ClosingOpenTelemetry(this.openTelemetrySdk);
        String opentelemetryPluginVersion = OtelUtils.getOpentelemetryPluginVersion();
        this.tracer.setDelegate(openTelemetry.getTracerProvider()
            .tracerBuilder(GlobalOpenTelemetrySdk.INSTRUMENTATION_NAME)
            .setInstrumentationVersion(opentelemetryPluginVersion)
            .build());
        this.meter = openTelemetry.getMeterProvider()
            .meterBuilder(GlobalOpenTelemetrySdk.INSTRUMENTATION_NAME)
            .setInstrumentationVersion(opentelemetryPluginVersion)
            .build();
        this.loggerProvider = openTelemetrySdk.getSdkLoggerProvider();
        this.eventEmitter = GlobalEventEmitterProvider.get()
            .eventEmitterBuilder(GlobalOpenTelemetrySdk.INSTRUMENTATION_NAME)
            .setInstrumentationVersion(opentelemetryPluginVersion)
            .setEventDomain("jenkins")
            .build();

        LOGGER.log(Level.INFO, () -> "OpenTelemetry SDK initialized: " + OtelUtils.prettyPrintOtelSdkConfig(autoConfiguredOpenTelemetrySdk));
    }

    @VisibleForTesting
    public void initializeNoOp() {
        LOGGER.log(Level.FINE, "initializeNoOp");

        this.openTelemetrySdk = null;
        this.resource = Resource.getDefault();
        this.config = ConfigPropertiesUtils.emptyConfig();
        this.openTelemetry = ClosingOpenTelemetry.noop();
        GlobalOpenTelemetry.resetForTest(); // hack for testing in Intellij cause by DiskUsageMonitoringInitializer
        GlobalLoggerProvider.resetForTest();
        GlobalEventEmitterProvider.resetForTest();
        GlobalOpenTelemetry.set(OpenTelemetry.noop());
        this.tracer.setDelegate(OpenTelemetry.noop().getTracer(GlobalOpenTelemetrySdk.INSTRUMENTATION_NAME));
        this.meter = OpenTelemetry.noop().getMeter(GlobalOpenTelemetrySdk.INSTRUMENTATION_NAME);
        this.loggerProvider = SdkLoggerProvider.builder().build();
        this.eventEmitter = EventEmitterProvider.noop().get(GlobalOpenTelemetrySdk.INSTRUMENTATION_NAME);
        LOGGER.log(Level.FINE, "OpenTelemetry initialized as NoOp");
    }

    static public OpenTelemetrySdkProvider get() {
        return ExtensionList.lookupSingleton(OpenTelemetrySdkProvider.class);
    }

    static class TracerDelegate implements Tracer {
        private Tracer delegate;

        @Override
        public synchronized SpanBuilder spanBuilder(String spanName) {
            return delegate.spanBuilder(spanName);
        }

        public synchronized void setDelegate(Tracer delegate) {
            this.delegate = delegate;
        }

        public synchronized Tracer getDelegate() {
            return delegate;
        }
    }
}
