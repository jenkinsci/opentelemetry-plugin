/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.jenkins.plugins.opentelemetry.opentelemetry.resource.JenkinsResource;
import io.jenkins.plugins.opentelemetry.opentelemetry.trace.TracerDelegate;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.GlobalMetricsProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
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
import java.util.Collections;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

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
        preDestroy();
        GlobalOpenTelemetry.resetForTest();
        initializeOpenTelemetrySdk(TESTING_METRICS_EXPORTER, TESTING_SPAN_EXPORTER, 500);
        LOGGER.log(Level.INFO, "OpenTelemetry initialized for TESTING");
    }

    public void initializeNoOp() {
        preDestroy();
        this.intervalMetricReader = null;

        // TRACES
        Resource resource = buildResource();
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().setResource(resource).build();
        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GlobalOpenTelemetry.set(openTelemetry);

        this.tracer.setDelegate(Tracer.getDefault());
        LOGGER.log(Level.INFO, "OpenTelemetry initialized as NoOp");
    }

    public void initializeForGrpc(@Nonnull String endpoint, boolean useTls, @Nullable String authenticationTokenHeaderName, @Nullable String authenticationTokenHeaderValue) {
        preDestroy();
        // GRPC CHANNEL
        ManagedChannelBuilder<?> managedChannelBuilder = ManagedChannelBuilder.forTarget(endpoint);
        if (useTls) {
            managedChannelBuilder.useTransportSecurity();
        } else {
            managedChannelBuilder.usePlaintext();
        }
        Metadata metadata = new Metadata();
        if (!Strings.isNullOrEmpty(authenticationTokenHeaderName)) {
            checkNotNull(authenticationTokenHeaderValue, "Null value not supported for authentication header '" + authenticationTokenHeaderName + "'");
            metadata.put(Metadata.Key.of(authenticationTokenHeaderName, ASCII_STRING_MARSHALLER), authenticationTokenHeaderValue);
        }
        if (!metadata.keys().isEmpty()) {
            managedChannelBuilder.intercept(MetadataUtils.newAttachHeadersInterceptor(metadata));
        }

        ManagedChannel grpcChannel = managedChannelBuilder.build();

        MetricExporter metricExporter = OtlpGrpcMetricExporter.builder().setChannel(grpcChannel).build();
        SpanExporter spanExporter = OtlpGrpcSpanExporter.builder().setChannel(grpcChannel).build();

        initializeOpenTelemetrySdk(metricExporter, spanExporter, 30_000);

        LOGGER.log(Level.INFO, () -> "OpenTelemetry initialized with GRPC endpoint " + endpoint + ", tls: " + useTls + ", authenticationHeader: " + Objects.toString(authenticationTokenHeaderName, ""));
    }

    protected void initializeOpenTelemetrySdk(MetricExporter metricExporter, SpanExporter spanExporter, int exportIntervalMillis) {
        // METRICS
        // See https://github.com/open-telemetry/opentelemetry-java/blob/v0.14.1/examples/otlp/src/main/java/io/opentelemetry/example/otlp/OtlpExporterExample.java
        this.intervalMetricReader =
                IntervalMetricReader.builder()
                        .setMetricExporter(metricExporter)
                        .setMetricProducers(Collections.singleton(sdkMeterProvider))
                        .setExportIntervalMillis(exportIntervalMillis)
                        .build();

        // TRACES
        Resource resource = buildResource();
        LOGGER.log(Level.INFO, () ->"OpenTelemetry SDK resources: " + resource.getAttributes().asMap().entrySet().stream().map( e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", ")));
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().setResource(resource).addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build();

        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GlobalOpenTelemetry.set(openTelemetry);

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
            this.openTelemetry.getTracerManagement().shutdown();
        }
        if (this.intervalMetricReader != null) {
            this.intervalMetricReader.shutdown();
        }
    }
}
