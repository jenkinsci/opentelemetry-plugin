/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.events.EventLogger;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.resources.ProcessResourceProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link OpenTelemetry} instance intended to live on the Jenkins Controller.
 */
@Extension(ordinal = Integer.MAX_VALUE)
public class JenkinsControllerOpenTelemetry implements ExtensionPoint {

    /**
     * See {@code OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS}
     */
    public static final String DEFAULT_OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS = ProcessResourceProvider.class.getName();

    public final static AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);

    @VisibleForTesting
    @Inject
    protected ReconfigurableOpenTelemetry openTelemetry;

    private Tracer defaultTracer;
    private Meter defaultMeter;
    private EventLogger defaultEventLogger;

    public JenkinsControllerOpenTelemetry() {
        super();
    }

    @PostConstruct
    public void postConstruct() {
        String opentelemetryPluginVersion = OtelUtils.getOpentelemetryPluginVersion();

        this.defaultTracer =
            this.openTelemetry
                .tracerBuilder(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
                .setInstrumentationVersion(opentelemetryPluginVersion)
                .build();

        this.defaultEventLogger = openTelemetry
            .eventLoggerBuilder(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
            .setInstrumentationVersion(opentelemetryPluginVersion)
            .build();

        this.defaultMeter = openTelemetry
            .meterBuilder(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
            .setInstrumentationVersion(opentelemetryPluginVersion)
            .build();
    }

    @NonNull
    public Tracer getDefaultTracer() {
        return defaultTracer;
    }

    @NonNull
    public Meter getDefaultMeter() {
        return defaultMeter;
    }

    @NonNull
    public EventLogger getDefaultEventLogger() {
        return defaultEventLogger;
    }

    public boolean isLogsEnabled() {
        String otelLogsExporter = openTelemetry.getConfig().getString("otel.logs.exporter");
        return otelLogsExporter != null && !otelLogsExporter.equals("none");
    }

    public boolean isOtelLogsMirrorToDisk() {
        String otelLogsExporter = openTelemetry.getConfig().getString("otel.logs.mirror_to_disk");
        return otelLogsExporter != null && otelLogsExporter.equals("true");
    }

    @VisibleForTesting
    @NonNull
    @Deprecated
    protected OpenTelemetrySdk getOpenTelemetrySdk() {
        return (OpenTelemetrySdk) Optional.ofNullable(openTelemetry).map(ReconfigurableOpenTelemetry::getImplementation).orElseThrow(() -> new IllegalStateException("OpenTelemetry not initialized"));
    }

    public void initialize(@NonNull OpenTelemetryConfiguration configuration) {
        openTelemetry.configure(
            configuration.toOpenTelemetryProperties(),
            configuration.toOpenTelemetryResource());
    }

    static public JenkinsControllerOpenTelemetry get() {
        return ExtensionList.lookupSingleton(JenkinsControllerOpenTelemetry.class);
    }
}
