/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import java.time.Duration;

/**
 * https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/testing-common/src/main/java/io/opentelemetry/instrumentation/testing/LibraryTestRunner.java#L87
 */
public class ReconfigurableMeterProviderITTest {

    private static final OpenTelemetrySdk openTelemetry;
    private static final InMemorySpanExporter testSpanExporter;
    private static final InMemoryMetricExporter testMetricExporter;
    private static final InMemoryLogRecordExporter testLogRecordExporter;
    private static final MetricReader metricReader;
    private static boolean forceFlushCalled;


    static {
        GlobalOpenTelemetry.resetForTest();

        testSpanExporter = InMemorySpanExporter.create();
        testMetricExporter = InMemoryMetricExporter.create(AggregationTemporality.DELTA);
        testLogRecordExporter = InMemoryLogRecordExporter.create();

        metricReader =
            PeriodicMetricReader.builder(testMetricExporter)
                // Set really long interval. We'll call forceFlush when we need the metrics
                // instead of collecting them periodically.
                .setInterval(Duration.ofNanos(Long.MAX_VALUE))
                .build();

        openTelemetry =
            OpenTelemetrySdk.builder()
                .setTracerProvider(
                    SdkTracerProvider.builder()
                        .addSpanProcessor(new FlushTrackingSpanProcessor())
                        .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
                        .addSpanProcessor(SimpleSpanProcessor.create(testSpanExporter))
                        .build())
                .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
                .setLoggerProvider(
                    SdkLoggerProvider.builder()
                        .addLogRecordProcessor(SimpleLogRecordProcessor.create(testLogRecordExporter))
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }

    private static class FlushTrackingSpanProcessor implements SpanProcessor {
        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
        }

        @Override
        public boolean isStartRequired() {
            return false;
        }

        @Override
        public void onEnd(ReadableSpan span) {
        }

        @Override
        public boolean isEndRequired() {
            return false;
        }

        @Override
        public CompletableResultCode forceFlush() {
            forceFlushCalled = true;
            return CompletableResultCode.ofSuccess();
        }
    }

}
