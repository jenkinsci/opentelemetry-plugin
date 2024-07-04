/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import com.google.common.base.Function;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure.ConfigPropertiesUtils;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.events.EventLogger;
import io.opentelemetry.api.incubator.events.EventLoggerProvider;
import io.opentelemetry.api.incubator.events.GlobalEventLoggerProvider;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.internal.SdkEventLoggerProvider;
import io.opentelemetry.sdk.resources.Resource;

import java.io.Closeable;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>
 * Reconfigurable {@link EventLoggerProvider} that allows to reconfigure the {@link Tracer}s,
 * {@link io.opentelemetry.api.logs.Logger}s, and {@link EventLogger}s.
 * </p>
 * <p>
 * We need reconfigurability because Jenkins supports changing the configuration of the OpenTelemetry params at runtime.
 * All instantiated tracers, loggers, and eventLoggers are reconfigured when the configuration changes, when
 * {@link ReconfigurableOpenTelemetry#configure(Map, Resource)} is invoked.
 * </p>
 */
public class ReconfigurableOpenTelemetry implements OpenTelemetry, Closeable {

    protected final Logger logger = Logger.getLogger(getClass().getName());
    Resource resource = Resource.empty();
    ConfigProperties config = ConfigPropertiesUtils.emptyConfig();
    OpenTelemetry openTelemetryImpl = OpenTelemetry.noop();
    final ReconfigurableMeterProvider meterProviderImpl = new ReconfigurableMeterProvider();
    final ReconfigurableTracerProvider traceProviderImpl = new ReconfigurableTracerProvider();
    final ReconfigurableEventLoggerProvider eventLoggerProviderImpl = new ReconfigurableEventLoggerProvider();
    final ReconfigurableLoggerProvider loggerProviderImpl = new ReconfigurableLoggerProvider();

    /**
     * Initialize as NoOp
     */
    public ReconfigurableOpenTelemetry() {
        try {
            GlobalOpenTelemetry.set(this);
        } catch (IllegalStateException e) {
            logger.log(Level.WARNING, "GlobalOpenTelemetry already set", e);
        }
        try {
            GlobalEventLoggerProvider.set(eventLoggerProviderImpl);
        } catch (IllegalStateException e) {
            logger.log(Level.WARNING, "GlobalEventLoggerProvider already set", e);
        }

        logger.log(Level.FINE, () -> "Initialize " +
            "GlobalOpenTelemetry with instance " + Optional.of(GlobalOpenTelemetry.get()).map(ot -> ot + "@" + System.identityHashCode(ot)) + "and " +
            "GlobalEventLoggerProvide with instance " + Optional.of(GlobalEventLoggerProvider.get()).map(elp -> elp + "@" + System.identityHashCode(elp)));
    }

    public void configure(@NonNull Map<String, String> openTelemetryProperties, Resource openTelemetryResource) {
        close(); // shutdown existing SDK
        if (openTelemetryProperties.containsKey("otel.exporter.otlp.endpoint") ||
            openTelemetryProperties.containsKey("otel.traces.exporter") ||
            openTelemetryProperties.containsKey("otel.metrics.exporter") ||
            openTelemetryProperties.containsKey("otel.logs.exporter")) {

            logger.log(Level.FINE, "initializeOtlp");

            // OPENTELEMETRY SDK
            OpenTelemetrySdk openTelemetrySdk = AutoConfiguredOpenTelemetrySdk
                .builder()
                // properties
                .addPropertiesSupplier(() -> openTelemetryProperties)
                .addPropertiesCustomizer((Function<ConfigProperties, Map<String, String>>) configProperties -> {
                    // keep a reference to the computed config properties for future use in the plugin
                    this.config = configProperties;
                    return Collections.emptyMap();
                })
                // resource
                .addResourceCustomizer((resource1, configProperties) -> {
                        // keep a reference to the computed Resource for future use in the plugin
                        this.resource = Resource.builder()
                            .putAll(resource1)
                            .putAll(openTelemetryResource).build();
                        return this.resource;
                    }
                )
                // disable shutdown hook, SDK closed by #close()
                .disableShutdownHook()
                .build()
                .getOpenTelemetrySdk();

            setOpenTelemetryImpl(openTelemetrySdk);

            logger.log(Level.INFO, () -> "OpenTelemetry initialized: " + OtelUtils.prettyPrintOtelSdkConfig(this.config, this.resource));

        } else { // NO-OP

            this.resource = Resource.empty();
            this.config = ConfigPropertiesUtils.emptyConfig();
            setOpenTelemetryImpl(OpenTelemetry.noop());

            logger.log(Level.INFO, "OpenTelemetry initialized as NoOp");
        }

        postOpenTelemetrySdkConfiguration();
    }

    public void setOpenTelemetryImpl(OpenTelemetry openTelemetryImpl) {
        this.openTelemetryImpl = openTelemetryImpl;
        this.meterProviderImpl.setDelegate(openTelemetryImpl.getMeterProvider());
        this.traceProviderImpl.setDelegate(openTelemetryImpl.getTracerProvider());
        this.loggerProviderImpl.setDelegate(openTelemetryImpl.getLogsBridge());
        this.eventLoggerProviderImpl.setDelegate(SdkEventLoggerProvider.create(openTelemetryImpl.getLogsBridge()));
    }

    @Override
    public void close() {
        logger.log(Level.FINE, "Shutdown...");

        // OTEL SDK
        if (this.openTelemetryImpl instanceof OpenTelemetrySdk) {
            logger.log(Level.FINE, () -> "Shutdown OTel SDK...");
            CompletableResultCode shutdown = ((OpenTelemetrySdk) this.openTelemetryImpl).shutdown();
            if (!shutdown.join(1, TimeUnit.SECONDS).isSuccess()) {
                logger.log(Level.WARNING, "Failure to shutdown OTel SDK");
            }
        }
        GlobalOpenTelemetry.resetForTest();
        GlobalEventLoggerProvider.resetForTest();
    }

    @Override
    public TracerProvider getTracerProvider() {
        return traceProviderImpl;
    }

    @Override
    public Tracer getTracer(String instrumentationScopeName) {
        return traceProviderImpl.get(instrumentationScopeName);
    }

    @Override
    public Tracer getTracer(String instrumentationScopeName, String instrumentationScopeVersion) {
        return traceProviderImpl.get(instrumentationScopeName, instrumentationScopeVersion);
    }

    @Override
    public TracerBuilder tracerBuilder(String instrumentationScopeName) {
        return traceProviderImpl.tracerBuilder(instrumentationScopeName);
    }

    @Override
    public MeterProvider getMeterProvider() {
        return meterProviderImpl;
    }

    public EventLoggerProvider getEventLoggerProvider() {
        return eventLoggerProviderImpl;
    }

    @Override
    public Meter getMeter(String instrumentationScopeName) {
        return meterProviderImpl.get(instrumentationScopeName);
    }

    @Override
    public MeterBuilder meterBuilder(String instrumentationScopeName) {
        return meterProviderImpl.meterBuilder(instrumentationScopeName);
    }

    protected OpenTelemetry getOpenTelemetryDelegate() {
        return openTelemetryImpl;
    }

    @NonNull
    public Resource getResource() {
        return resource;
    }

    @NonNull
    public ConfigProperties getConfig() {
        return config;
    }

    @Override
    public LoggerProvider getLogsBridge() {
        return loggerProviderImpl;
    }

    @Override
    public ContextPropagators getPropagators() {
        return openTelemetryImpl.getPropagators();
    }

    /**
     * For extension purpose
     */
    protected void postOpenTelemetrySdkConfiguration() {
    }

}
