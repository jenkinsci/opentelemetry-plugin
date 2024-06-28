/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.Assert;
import org.junit.Test;

/**
 * https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/testing-common/src/main/java/io/opentelemetry/instrumentation/testing/LibraryTestRunner.java#L87
 */
public class ReconfigurableMeterProviderITTest {


    @Test
    public void testPlainOpenTelemetrySdk() {
        try (OpenTelemetryTest openTelemetryTest = newOpenTelemetryTest()) {
            InMemoryMetricReader metricReader = openTelemetryTest.metricReader;
            MeterProvider meterProvider = openTelemetryTest.openTelemetrySdk.getMeterProvider();
            Meter meter = meterProvider.meterBuilder("test-meter").build();

            LongCounter longCounter = meter.counterBuilder("test.long.counter").build();
            longCounter.add(1);
            assertMetricExist("test.long.counter", metricReader);

            ObservableLongMeasurement testLongCounterObserver = meter.counterBuilder("test.long.counter.observer").buildObserver();
            meter.batchCallback(() -> testLongCounterObserver.record(1), testLongCounterObserver);
            assertMetricExist("test.long.counter.observer", metricReader);

             meter.counterBuilder("test.long.counter.callback").buildWithCallback(observableLongMeasurement -> observableLongMeasurement.record(1));
            assertMetricExist("test.long.counter.callback", metricReader);

            // UP DOWN COUNTER
            LongUpDownCounter longUpDownCounter = meter.upDownCounterBuilder("test.long.up.down.counter").build();
            longUpDownCounter.add(1);
            assertMetricExist("test.long.up.down.counter", metricReader);
        }
    }

    @Test
    public void testReconfigurableOpenTelemetrySdk() {
        try (OpenTelemetryTest openTelemetryTest = newOpenTelemetryTest()) {
            InMemoryMetricReader metricReader = openTelemetryTest.metricReader;
            MeterProvider meterProvider = new ReconfigurableMeterProvider(openTelemetryTest.openTelemetrySdk.getMeterProvider());
            Meter meter = meterProvider.meterBuilder("test-meter").build();

            LongCounter longCounter = meter.counterBuilder("test.long.counter").build();
            longCounter.add(1);
            assertMetricExist("test.long.counter", metricReader);

            ObservableLongMeasurement testLongCounterObserver = meter.counterBuilder("test.long.counter.observer").buildObserver();
            BatchCallback batchCallback = meter.batchCallback(() -> testLongCounterObserver.record(1), testLongCounterObserver);

            assertMetricExist("test.long.counter.observer", metricReader);
        }
    }

    @Test
    public void testReconfigurableOpenTelemetrySdkAfterNopInitialization() {
        ReconfigurableMeterProvider meterProvider = new ReconfigurableMeterProvider();
        Meter meter = meterProvider.meterBuilder("test-meter").build();

        // LONG COUNTER
        LongCounter longCounter = meter.counterBuilder("test.long.counter").build();
        longCounter.add(1);

        ObservableLongMeasurement testLongCounterObserverMeasurement = meter.counterBuilder("test.long.counter.observer").buildObserver();
        Runnable testLongCounterObserverCallback = () -> testLongCounterObserverMeasurement.record(1);
        meter.batchCallback(testLongCounterObserverCallback, testLongCounterObserverMeasurement);

        meter.counterBuilder("test.long.counter.callback").buildWithCallback(observableLongMeasurement -> observableLongMeasurement.record(1));

        // UP DOWN COUNTER
        LongUpDownCounter longUpDownCounter = meter.upDownCounterBuilder("test.long.up.down.counter").build();
        longUpDownCounter.add(1);
        // long up down counter callback
        meter.upDownCounterBuilder("test.long.up.down.counter.callback").buildWithCallback(observableLongMeasurement -> observableLongMeasurement.record(1));
        // long up down counter observer
        ObservableLongMeasurement testLongUpDownCounterObserverMeasurement = meter.upDownCounterBuilder("test.long.up.down.counter.observer").buildObserver();
        meter.batchCallback(() -> testLongUpDownCounterObserverMeasurement.record(1), testLongUpDownCounterObserverMeasurement);

        try (OpenTelemetryTest openTelemetryTest = newOpenTelemetryTest()) {

            meterProvider.setDelegate(openTelemetryTest.openTelemetrySdk.getMeterProvider());
            InMemoryMetricReader metricReader = openTelemetryTest.metricReader;

            // LONG COUNTER
            assertMetricDoesntExist("test.long.counter", metricReader);
            longCounter.add(2);
            assertMetricExist("test.long.counter", metricReader);

            // LONG COUNTER OBSERVER
            // "test.long.counter.observer" was registered before the reconfiguration and should continue to exist
            assertMetricExist("test.long.counter.observer", metricReader);

            // LONG COUNTER CALLBACK
            // "test.long.counter.callback" was registered before the reconfiguration and should continue to exist
            assertMetricExist("test.long.counter.callback", metricReader);

            // LONG UP DOWN COUNTER
            longUpDownCounter.add(2);
            assertMetricExist("test.long.up.down.counter", metricReader);

            // LONG UP DOWN COUNTER CALLBACK
            // "test.long.up.down.counter.callback" was registered before the reconfiguration and should continue to exist
            assertMetricExist("test.long.up.down.counter.callback", metricReader);

            // LONG UP DOWN COUNTER OBSERVER
            // "test.long.up.down.counter.observer" was registered before the reconfiguration and should continue to exist
            assertMetricExist("test.long.up.down.counter.observer", metricReader);

        }

    }


    private static void assertMetricExist(String metricName, InMemoryMetricReader metricReader) {
        Assert.assertTrue("Metric '" + metricName + "' was not exported", metricReader.collectAllMetrics().stream().anyMatch(metricData -> metricName.equals(metricData.getName())));
    }

    private static void assertMetricDoesntExist(String metricName, InMemoryMetricReader metricReader) {
        Assert.assertTrue("Metric '" + metricName + "' should not exist", metricReader.collectAllMetrics().stream().noneMatch(metricData -> metricName.equals(metricData.getName())));
    }

    OpenTelemetryTest newOpenTelemetryTest() {

        InMemoryLogRecordExporter testLogRecordExporter = InMemoryLogRecordExporter.create();

        InMemoryMetricReader metricReader = InMemoryMetricReader.create();

        OpenTelemetrySdk openTelemetryImpl =
            OpenTelemetrySdk.builder()
                .setTracerProvider(
                    SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
                        .addSpanProcessor(SimpleSpanProcessor.create(InMemorySpanExporter.create()))
                        .build())
                .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
                .setLoggerProvider(
                    SdkLoggerProvider.builder()
                        .addLogRecordProcessor(SimpleLogRecordProcessor.create(testLogRecordExporter))
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        return new OpenTelemetryTest(openTelemetryImpl, metricReader);
    }

    static class OpenTelemetryTest implements AutoCloseable {
        final OpenTelemetrySdk openTelemetrySdk;
        final InMemoryMetricReader metricReader;

        public OpenTelemetryTest(OpenTelemetrySdk openTelemetrySdk, InMemoryMetricReader metricReader) {
            this.openTelemetrySdk = openTelemetrySdk;
            this.metricReader = metricReader;
        }

        @Override
        public void close() {
            this.openTelemetrySdk.shutdown();
        }
    }
}
