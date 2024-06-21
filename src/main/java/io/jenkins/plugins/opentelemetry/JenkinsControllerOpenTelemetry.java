/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import io.jenkins.plugins.opentelemetry.opentelemetry.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.events.EventLogger;
import io.opentelemetry.api.incubator.events.GlobalEventLoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.resources.ProcessResourceProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;

import javax.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * {@link OpenTelemetry} instance intended to live on the Jenkins Controller.
 */
@Extension
public class JenkinsControllerOpenTelemetry extends ReconfigurableOpenTelemetry implements OpenTelemetry {

    private static final Logger LOGGER = Logger.getLogger(JenkinsControllerOpenTelemetry.class.getName());

    /**
     * See {@code OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS}
     */
    public static final String DEFAULT_OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS = ProcessResourceProvider.class.getName();

    public final static AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);

    @NonNull
    private final transient Tracer defaultTracer;
    protected transient Meter defaultMeter;
    protected final transient EventLogger defaultEventLogger;

    public JenkinsControllerOpenTelemetry() {
        super();
        if (INSTANCE_COUNTER.get() > 0) {
            LOGGER.log(Level.WARNING, "More than one instance of JenkinsControllerOpenTelemetry created: " + INSTANCE_COUNTER.get());
        }

        String opentelemetryPluginVersion = OtelUtils.getOpentelemetryPluginVersion();

        this.defaultTracer =
            getTracerProvider()
                .tracerBuilder(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
                .setInstrumentationVersion(opentelemetryPluginVersion)
                .build();

        this.defaultEventLogger = getEventLoggerProvider()
            .eventLoggerBuilder(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
            .setInstrumentationVersion(opentelemetryPluginVersion)
            .build();
    }

    @NonNull
    public Tracer getDefaultTracer() {
        return defaultTracer;
    }

    public boolean isLogsEnabled() {
        String otelLogsExporter = config.getString("otel.logs.exporter");
        return otelLogsExporter != null && !otelLogsExporter.equals("none");
    }

    public boolean isOtelLogsMirrorToDisk() {
        String otelLogsExporter = config.getString("otel.logs.mirror_to_disk");
        return otelLogsExporter != null && otelLogsExporter.equals("true");
    }

    @VisibleForTesting
    @NonNull
    protected OpenTelemetrySdk getOpenTelemetrySdk() {
        Preconditions.checkNotNull(getOpenTelemetryDelegate());
        if (getOpenTelemetryDelegate() instanceof OpenTelemetrySdk) {
            return (OpenTelemetrySdk) getOpenTelemetryDelegate();
        } else {
            throw new IllegalStateException("OpenTelemetry initialized as NoOp");
        }
    }

    @PreDestroy
    public void shutdown() {
        super.close();
    }

    public void initialize(@NonNull OpenTelemetryConfiguration configuration) {
        configure(
            configuration.toOpenTelemetryProperties(),
            configuration.toOpenTelemetryResource());
    }

    @Override
    protected void postOpenTelemetrySdkConfiguration() {
        String opentelemetryPluginVersion = OtelUtils.getOpentelemetryPluginVersion();

        this.defaultMeter = getMeterProvider()
            .meterBuilder(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
            .setInstrumentationVersion(opentelemetryPluginVersion)
            .build();

        LOGGER.log(Level.FINER, () -> "Configure OpenTelemetryLifecycleListeners: " + ExtensionList.lookup(OpenTelemetryLifecycleListener.class).stream().sorted().map(e -> e.getClass().getName()).collect(Collectors.joining(", ")));
        ExtensionList.lookup(OpenTelemetryLifecycleListener.class).stream()
            .sorted()
            .forEachOrdered(otelComponent -> {
                otelComponent.afterSdkInitialized(defaultMeter, getOpenTelemetryDelegate().getLogsBridge(), defaultEventLogger, defaultTracer, config);
                otelComponent.afterSdkInitialized(getOpenTelemetryDelegate(), config);
            });
    }

    static public JenkinsControllerOpenTelemetry get() {
        return ExtensionList.lookupSingleton(JenkinsControllerOpenTelemetry.class);
    }
}
