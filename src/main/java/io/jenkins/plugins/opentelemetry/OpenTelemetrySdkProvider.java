/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.ExtensionList;
import io.jenkins.plugins.opentelemetry.opentelemetry.ClosingOpenTelemetry;
import io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure.ConfigPropertiesUtils;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.events.EventEmitterProvider;
import io.opentelemetry.api.events.GlobalEventEmitterProvider;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.resources.ProcessResourceProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;

import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

    protected transient Resource resource;
    protected transient ConfigProperties config;
    @Nullable
    protected transient OpenTelemetrySdk openTelemetrySdk;
    @NonNull
    private final transient TracerDelegate tracer = new TracerDelegate();
    protected transient Meter meter;
    protected transient EventEmitter eventEmitter;

    public OpenTelemetrySdkProvider() {
    }


    @NonNull
    public Tracer getTracer() {
        return Preconditions.checkNotNull(tracer.getDelegate());
    }

    @NonNull
    public Meter getMeter() {
        return Preconditions.checkNotNull(meter);
    }

    @NonNull
    public Resource getResource() {
        return Preconditions.checkNotNull(resource);
    }

    @NonNull
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

    @NonNull
    public ContextPropagators getPropagators() {
        return openTelemetry.getPropagators();
    }

    @NonNull
    public LoggerProvider getLoggerProvider() {
        return openTelemetry.getLogsBridge();
    }

    @VisibleForTesting
    @NonNull
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
            CompletableResultCode shutdown = this.openTelemetrySdk.shutdown();
            if(!shutdown.join(1, TimeUnit.SECONDS).isSuccess()) {
                LOGGER.log(Level.WARNING, "Failure to shutdown OTel SDK");
            }
        }
        GlobalOpenTelemetry.resetForTest();
        GlobalEventEmitterProvider.resetForTest();
    }

    public void initialize(@NonNull OpenTelemetryConfiguration configuration) {
        shutdown(); // shutdown existing SDK
        if (configuration.getEndpoint().isPresent()) {
            initializeOtlp(configuration);
        } else {
            initializeNoOp();
        }
        LOGGER.log(Level.FINE, () -> "Initialize Otel SDK on components: " + ExtensionList.lookup(OtelComponent.class).stream().sorted().map(e -> e.getClass().getName()).collect(Collectors.joining(", ")));
        ExtensionList.lookup(OtelComponent.class).stream().sorted().forEachOrdered(otelComponent -> {
            otelComponent.afterSdkInitialized(meter, openTelemetry.getLogsBridge(), eventEmitter, tracer, config);
            otelComponent.afterSdkInitialized(openTelemetry, config);
        });
    }

    public void initializeOtlp(@NonNull OpenTelemetryConfiguration configuration) {

        AutoConfiguredOpenTelemetrySdkBuilder sdkBuilder = AutoConfiguredOpenTelemetrySdk.builder();
        // PROPERTIES
        sdkBuilder.addPropertiesSupplier(configuration::toOpenTelemetryProperties);
        sdkBuilder.addPropertiesCustomizer((Function<ConfigProperties, Map<String, String>>) configProperties -> {
            // keep a reference to the computed config properties for future use in the plugin
            OpenTelemetrySdkProvider.this.config = configProperties;
            return Collections.emptyMap();
        });

        // RESOURCE
        sdkBuilder.addResourceCustomizer((resource, configProperties) -> {
            // keep a reference to the computed Resource for future use in the plugin
            this.resource = Resource.builder()
                .putAll(resource)
                .putAll(configuration.toOpenTelemetryResource()).build();
            return this.resource;
            }
        );

        sdkBuilder
            .disableShutdownHook() // SDK closed by io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider.preDestroy()
            .setResultAsGlobal(); // ensure GlobalOpenTelemetry.set() is invoked
        this.openTelemetrySdk = sdkBuilder.build().getOpenTelemetrySdk();
        this.openTelemetry = new ClosingOpenTelemetry(this.openTelemetrySdk);
        String opentelemetryPluginVersion = OtelUtils.getOpentelemetryPluginVersion();
        this.tracer.setDelegate(openTelemetry.getTracerProvider()
            .tracerBuilder(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
            .setInstrumentationVersion(opentelemetryPluginVersion)
            .build());
        this.meter = openTelemetry.getMeterProvider()
            .meterBuilder(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
            .setInstrumentationVersion(opentelemetryPluginVersion)
            .build();
        this.eventEmitter = GlobalEventEmitterProvider.get()
            .eventEmitterBuilder(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
            .setInstrumentationVersion(opentelemetryPluginVersion)
            .setEventDomain("jenkins")
            .build();

        LOGGER.log(Level.INFO, () -> "OpenTelemetry SDK initialized: " + OtelUtils.prettyPrintOtelSdkConfig(this.config, this.resource));
    }

    @VisibleForTesting
    public void initializeNoOp() {
        LOGGER.log(Level.FINE, "initializeNoOp");

        this.openTelemetrySdk = null;
        this.resource = Resource.getDefault();
        this.config = ConfigPropertiesUtils.emptyConfig();
        this.openTelemetry = ClosingOpenTelemetry.noop();
        GlobalOpenTelemetry.resetForTest(); // hack for testing in Intellij cause by DiskUsageMonitoringInitializer
        GlobalEventEmitterProvider.resetForTest();
        GlobalOpenTelemetry.set(OpenTelemetry.noop());
        this.tracer.setDelegate(OpenTelemetry.noop().getTracer(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME));
        this.meter = OpenTelemetry.noop().getMeter(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME);
        this.eventEmitter = EventEmitterProvider.noop().get(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME);
        LOGGER.log(Level.FINE, "OpenTelemetry initialized as NoOp");
    }

    static public OpenTelemetrySdkProvider get() {
        return ExtensionList.lookupSingleton(OpenTelemetrySdkProvider.class);
    }

    static class TracerDelegate implements Tracer {
        private Tracer delegate;

        @Override
        public synchronized SpanBuilder spanBuilder(@Nonnull String spanName) {
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
