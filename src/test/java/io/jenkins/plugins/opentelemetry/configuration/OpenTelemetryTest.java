/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.configuration;

import com.google.common.base.Strings;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

public class OpenTelemetryTest {

    @Test
    public void test() throws InterruptedException {

        int port = 8200;
        port = 4317;
        final String endpoint = "http://127.0.0.1:" + port;
        String secretToken = "secret_token";
        Map<String, String> headers = new HashMap<>();
        if (true) {
            headers.put("Authorization", "Bearer " + secretToken);
        }
        int timeoutMillis = 1_000;

        final OtlpGrpcMetricExporterBuilder metricExporterBuilder = OtlpGrpcMetricExporter.builder();
        final OtlpGrpcSpanExporterBuilder spanExporterBuilder = OtlpGrpcSpanExporter.builder();

        spanExporterBuilder.setEndpoint(endpoint);
        metricExporterBuilder.setEndpoint(endpoint);

        spanExporterBuilder.setTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
        metricExporterBuilder.setTimeout(timeoutMillis, TimeUnit.MILLISECONDS);

        for (Map.Entry<String, String> header: headers.entrySet()) {
            spanExporterBuilder.addHeader(header.getKey(), header.getValue());
            metricExporterBuilder.addHeader(header.getKey(), header.getValue());
        }
        SpanExporter spanExporter = spanExporterBuilder.build();
        MetricExporter metricExporter = metricExporterBuilder.build();

        final Resource resource = Resource.getDefault();
        final SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder().setResource(resource).buildAndRegisterGlobal();

        IntervalMetricReader intervalMetricReader = IntervalMetricReader.builder()
                .setMetricExporter(metricExporter)
                .setMetricProducers(Collections.singleton(sdkMeterProvider))
                .setExportIntervalMillis(200)
                .build();

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().setResource(resource).addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build();

        final OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

        final Tracer tracer = openTelemetrySdk.getTracer(this.getClass().getName());
        final Span rootSpan = tracer.spanBuilder("test-trace").startSpan();
        try (final Scope rootScope = rootSpan.makeCurrent()) {
            final Span span1 = tracer.spanBuilder("first-span").startSpan();
            try (Scope scope1 = span1.makeCurrent()) {
                Thread.sleep(10);
            }
        } finally {
            rootSpan.end();
        }

        intervalMetricReader.shutdown();
        final CompletableResultCode shutdown = openTelemetrySdk.getSdkTracerProvider().shutdown();
        shutdown.join(1_000, TimeUnit.MILLISECONDS);
        MatcherAssert.assertThat(shutdown.isSuccess(), Matchers.is(true));

    }
}
