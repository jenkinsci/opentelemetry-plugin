/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
import io.jenkins.plugins.opentelemetry.opentelemetry.metrics.exporter.NoOpMetricExporter;
import io.jenkins.plugins.opentelemetry.opentelemetry.resource.JenkinsResource;
import io.jenkins.plugins.opentelemetry.opentelemetry.trace.TracerDelegate;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.GlobalMetricsProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

@Extension
public class OpenTelemetrySdkProvider {
    @SuppressFBWarnings
    protected static boolean TESTING_INMEMORY_MODE = false;
    @SuppressFBWarnings
    protected static MetricExporter TESTING_METRICS_EXPORTER;
    @SuppressFBWarnings
    protected static SpanExporter TESTING_SPAN_EXPORTER;

    private static Logger LOGGER = Logger.getLogger(OpenTelemetrySdkProvider.class.getName());

    protected transient OpenTelemetrySdk openTelemetry;

    protected transient TracerDelegate tracer;

    protected transient  SdkMeterProvider sdkMeterProvider;

    protected transient Meter meter;

    protected transient IntervalMetricReader intervalMetricReader;

    public OpenTelemetrySdkProvider() {

    }

    @PostConstruct
    @VisibleForTesting
    public void postConstruct() {
        this.tracer = new TracerDelegate(Tracer.getDefault());
        Resource resource = buildResource();
        this.sdkMeterProvider = SdkMeterProvider.builder().setResource(resource).buildAndRegisterGlobal();
        this.meter = GlobalMetricsProvider.getMeter("jenkins");
    }

    public void initializeForTesting() {
        LOGGER.log(Level.FINE, "initializeForTesting");
        preDestroy();

        initializeOpenTelemetrySdk(TESTING_METRICS_EXPORTER, TESTING_SPAN_EXPORTER, 500);
        LOGGER.log(Level.INFO, "OpenTelemetry initialized for TESTING");
    }

    public void initializeNoOp() {
        LOGGER.log(Level.FINE, "initializeNoOp");
        preDestroy();
        this.intervalMetricReader = null;

        // TRACES
        Resource resource = buildResource();
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().setResource(resource).build();
        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

        this.tracer.setDelegate(Tracer.getDefault());
        LOGGER.log(Level.FINE, "OpenTelemetry initialized as NoOp");
    }

    /**
     *
     * @param endpoint "http://host:port", "https://host:port"
     */
    public void initializeForGrpc(@Nonnull String endpoint, @Nullable String trustedCertificatesPem, @Nonnull OtlpAuthentication otlpAuthentication) {
        Preconditions.checkArgument(endpoint.startsWith("http://") || endpoint.startsWith("https://"), "endpoint must be prefixed by 'http://' or 'https://': %s", endpoint);
        LOGGER.log(Level.FINE, "initializeForGrpc");

        preDestroy();

        // TODO variabilize
        int timeoutMillis = 30_000;
        int exportIntervalMillis = 60_000;

        final OtlpGrpcMetricExporterBuilder metricExporterBuilder = OtlpGrpcMetricExporter.builder();
        final OtlpGrpcSpanExporterBuilder spanExporterBuilder = OtlpGrpcSpanExporter.builder();

        spanExporterBuilder.setEndpoint(endpoint);
        metricExporterBuilder.setEndpoint(endpoint);

        spanExporterBuilder.setTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
        metricExporterBuilder.setTimeout(timeoutMillis, TimeUnit.MILLISECONDS);

        otlpAuthentication.configure(spanExporterBuilder);
        otlpAuthentication.configure(metricExporterBuilder);

        if (!Strings.isNullOrEmpty(trustedCertificatesPem)) {
            final byte[] trustedCertificatesPemBytes = trustedCertificatesPem.getBytes(StandardCharsets.UTF_8);
            spanExporterBuilder.setTrustedCertificates(trustedCertificatesPemBytes);
            // FIXME not yet supported on OtlpGrpcMetricExporterBuilder
            // See https://github.com/open-telemetry/opentelemetry-java/blob/v1.0.0/exporters/otlp/metrics/src/main/java/io/opentelemetry/exporter/otlp/metrics/OtlpGrpcMetricExporterBuilder.java
            // See https://github.com/open-telemetry/opentelemetry-java/blob/v1.0.0/exporters/otlp/trace/src/main/java/io/opentelemetry/exporter/otlp/trace/OtlpGrpcSpanExporterBuilder.java#L141
        }

        SpanExporter spanExporter = spanExporterBuilder.build();
        MetricExporter metricExporter = metricExporterBuilder.build();

        if(!Strings.isNullOrEmpty(trustedCertificatesPem)) {
            // FIXME remove once OtlpGrpcMetricExporterBuilder supports #setTrustedCertificates()
            LOGGER.log(Level.WARNING, "Metrics Exporter don't support trusted certificates yet, use a NoOpMetricsExporter");
            metricExporter = new NoOpMetricExporter();
        }

        initializeOpenTelemetrySdk(metricExporter, spanExporter, exportIntervalMillis);

        LOGGER.log(Level.INFO, () -> "OpenTelemetry initialized with GRPC endpoint " + endpoint + ", authenticationHeader: " + Objects.toString(otlpAuthentication, ""));
    }

    protected void initializeOpenTelemetrySdk(MetricExporter metricExporter, SpanExporter spanExporter, int metricsExportIntervalMillis) {
        // METRICS
        // See https://github.com/open-telemetry/opentelemetry-java/blob/v0.14.1/examples/otlp/src/main/java/io/opentelemetry/example/otlp/OtlpExporterExample.java
        this.intervalMetricReader =
                IntervalMetricReader.builder()
                        .setMetricExporter(metricExporter)
                        .setMetricProducers(Collections.singleton(sdkMeterProvider))
                        .setExportIntervalMillis(metricsExportIntervalMillis)
                        .build();

        // TRACES
        Resource resource = buildResource();
        LOGGER.log(Level.FINE, () ->"OpenTelemetry SDK resources: " + resource.getAttributes().asMap().entrySet().stream().map( e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", ")));
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().setResource(resource).addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build();

        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

        this.tracer.setDelegate(openTelemetry.getTracer("jenkins"));
    }

    /**
     * TODO refresh {@link JenkinsResource} when {@link Jenkins#getRootUrl()} changes
     */
    private Resource buildResource() {
        return Resource.getDefault().merge(new JenkinsResource().create());
    }

    @Nonnull
    public Tracer getTracer() {
        return tracer;
    }

    @Nonnull
    public Meter getMeter() {
        return meter;
    }

    @VisibleForTesting
    @Nonnull
    protected OpenTelemetrySdk getOpenTelemetrySdk() {
        return openTelemetry;
    }


    @PreDestroy
    public void preDestroy() {
        if (this.openTelemetry != null) {
            this.openTelemetry.getSdkTracerProvider().shutdown();
        }
        if (this.intervalMetricReader != null) {
            this.intervalMetricReader.shutdown();
        }
        GlobalOpenTelemetry.resetForTest();
    }

    public void initialize(@Nonnull OpenTelemetryConfiguration configuration) {
        if (configuration.getEndpoint() == null) {
            initializeNoOp();
        } else {
            initializeForGrpc(configuration.getEndpoint(), configuration.getTrustedCertificatesPem(), configuration.getAuthentication());
        }
    }
}
