/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.ObservableDoubleCounter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.ExperimentalMemoryPools;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import org.junit.Test;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/*
https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/testing-common/src/main/java/io/opentelemetry/instrumentation/testing/LibraryTestRunner.java#L87
 */

public class ReconfigurableMeterProviderTest {

    final static Random random = new Random();


    @Test
    public void testBatchCallback() throws Exception {
        AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk1 = AutoConfiguredOpenTelemetrySdk.builder().addPropertiesSupplier(() -> Map.of("otel.metric.export.interval", "1000")).build();
        OpenTelemetrySdk openTelemetrySdk1 = autoConfiguredOpenTelemetrySdk1.getOpenTelemetrySdk();
        final ReconfigurableMeterProvider meterProvider = new ReconfigurableMeterProvider(openTelemetrySdk1.getMeterProvider());

        ReconfigurableMeterProvider.ReconfigurableMeter reconfigurableJenkinsMeter = (ReconfigurableMeterProvider.ReconfigurableMeter) meterProvider
            .meterBuilder("io.jenkins")
            .setInstrumentationVersion("1.0.0")
            .build();
        final ObservableLongMeasurement observableLongMeasurement = reconfigurableJenkinsMeter.counterBuilder("jenkins.build.counter").buildObserver();

        // see ExperimentalMemoryPools
        BatchCallback batchCallback = reconfigurableJenkinsMeter.batchCallback(() -> {
            int value = random.nextInt(5);
            System.out.println("record " + value);
            observableLongMeasurement.record(value);
        }, observableLongMeasurement);


        // RECONFIGURE
        AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk2 = AutoConfiguredOpenTelemetrySdk.builder().addPropertiesSupplier(() -> Map.of("otel.metric.export.interval", "1000")).build();
        OpenTelemetrySdk openTelemetrySdk2 = autoConfiguredOpenTelemetrySdk2.getOpenTelemetrySdk();
        meterProvider.setDelegate(openTelemetrySdk2.getMeterProvider());

        openTelemetrySdk1.shutdown();


        System.out.println("sleeping 0s");
        Thread.sleep(2_000);
        openTelemetrySdk2.shutdown();


    }

    @org.junit.Test
    public void test() {
        ReconfigurableMeterProvider meterProvider = new ReconfigurableMeterProvider();

        ReconfigurableMeterProviderTest.MeterProviderMock meterProviderImpl_1 = new ReconfigurableMeterProviderTest.MeterProviderMock();
        meterProvider.setDelegate(meterProviderImpl_1);

        ReconfigurableMeterProvider.ReconfigurableMeter jenkinsMeter = (ReconfigurableMeterProvider.ReconfigurableMeter) meterProvider
            .meterBuilder("io.jenkins")
            .setInstrumentationVersion("1.0.0")
            .build();

        ReconfigurableMeterProviderTest.MeterMock jenkinsMeterImpl = (ReconfigurableMeterProviderTest.MeterMock) jenkinsMeter.delegate;
        assertEquals("io.jenkins", jenkinsMeterImpl.instrumentationScopeName);
        assertNull(jenkinsMeterImpl.schemaUrl);
        assertEquals("1.0.0", jenkinsMeterImpl.instrumentationVersion);
        assertEquals(meterProviderImpl_1.id, jenkinsMeterImpl.meterProviderId);


        ReconfigurableMeterProvider.ReconfigurableMeter myFrameworkMeter = (ReconfigurableMeterProvider.ReconfigurableMeter) meterProvider
            .meterBuilder("io.myframework")
            .setSchemaUrl("https://myframework.io/")
            .build();
        ReconfigurableMeterProviderTest.MeterMock myFrameworkMeterImpl = (ReconfigurableMeterProviderTest.MeterMock) myFrameworkMeter.delegate;
        assertEquals("io.myframework", myFrameworkMeterImpl.instrumentationScopeName);
        assertEquals("https://myframework.io/", myFrameworkMeterImpl.schemaUrl);
        assertNull(myFrameworkMeterImpl.instrumentationVersion);
        assertEquals(meterProviderImpl_1.id, myFrameworkMeterImpl.meterProviderId);

        ReconfigurableMeterProvider.ReconfigurableMeter myFrameworkMeterShouldBeTheSameInstance = (ReconfigurableMeterProvider.ReconfigurableMeter) meterProvider
            .meterBuilder("io.myframework")
            .setSchemaUrl("https://myframework.io/")
            .build();

        assertEquals(myFrameworkMeter, myFrameworkMeterShouldBeTheSameInstance);

        // #### COUNTER ####

        // Long Counter
        ReconfigurableMeterProvider.ReconfigurableLongCounter jenkinsBuildCounter = (ReconfigurableMeterProvider.ReconfigurableLongCounter) jenkinsMeter.counterBuilder("jenkins.build.counter").build();
        LongCounterMock jenkinsBuildCounterImpl = (LongCounterMock) jenkinsBuildCounter.getDelegate();
        assertEquals(meterProviderImpl_1.id, jenkinsBuildCounterImpl.meterProviderId);

        // Observable Long Counter
        ReconfigurableMeterProvider.ReconfigurableObservableLongCounter availableExecutorObservableCounter = (ReconfigurableMeterProvider.ReconfigurableObservableLongCounter) jenkinsMeter.counterBuilder(JenkinsSemanticMetrics.JENKINS_EXECUTOR_AVAILABLE).buildWithCallback(observableLongMeasurement -> observableLongMeasurement.record(random.nextInt(5)));
        ObservableLongCounterMock observableLongCounterMock = (ObservableLongCounterMock) availableExecutorObservableCounter.delegate;
        assertEquals(meterProviderImpl_1.id, observableLongCounterMock.meterProviderId);

        // Observable Long Measurement
        ReconfigurableMeterProvider.ReconfigurableObservableLongMeasurement busyExecutorObservableMeasurement = (ReconfigurableMeterProvider.ReconfigurableObservableLongMeasurement) jenkinsMeter.counterBuilder(JenkinsSemanticMetrics.JENKINS_EXECUTOR_BUSY).buildObserver();
        ObservableLongMeasurementMock observableLongMeasurementMock = (ObservableLongMeasurementMock) busyExecutorObservableMeasurement.getDelegate();
        assertEquals(meterProviderImpl_1.id, observableLongMeasurementMock.meterProviderId);

        // Double Counter
        ReconfigurableMeterProvider.ReconfigurableDoubleCounter jenkinsBuildDurationCounter = (ReconfigurableMeterProvider.ReconfigurableDoubleCounter) jenkinsMeter.counterBuilder("jenkins.build.duration.counter").ofDoubles().build();
        DoubleCounterMock jenkinsBuildDurationCounterImpl = (DoubleCounterMock) jenkinsBuildDurationCounter.getDelegate();
        assertEquals(meterProviderImpl_1.id, jenkinsBuildDurationCounterImpl.meterProviderId);

        // Observable Double Counter
        ReconfigurableMeterProvider.ReconfigurableObservableDoubleCounter observableDoubleCounter = (ReconfigurableMeterProvider.ReconfigurableObservableDoubleCounter) jenkinsMeter.counterBuilder("observable.double.counter").ofDoubles().buildWithCallback(observableDoubleMeasurement -> observableDoubleMeasurement.record(random.nextDouble()));
        ObservableDoubleCounterMock observableDoubleCounterImpl = (ObservableDoubleCounterMock) observableDoubleCounter.getDelegate();
        assertEquals(meterProviderImpl_1.id, observableDoubleCounterImpl.meterProviderId);

        // Observable Double Measurement
        ReconfigurableMeterProvider.ReconfigurableObservableDoubleMeasurement observableDoubleMeasurement = (ReconfigurableMeterProvider.ReconfigurableObservableDoubleMeasurement) jenkinsMeter.counterBuilder("observable.double.measurement").ofDoubles().buildObserver();
        ObservableDoubleMeasurementMock observableDoubleMeasurementImpl = (ObservableDoubleMeasurementMock) observableDoubleMeasurement.getDelegate();
        assertEquals(meterProviderImpl_1.id, observableDoubleMeasurementImpl.meterProviderId);

        // #### GAUGE ####

        // Double Gauge
        ReconfigurableMeterProvider.ReconfigurableDoubleGauge memoryUsedGauge = (ReconfigurableMeterProvider.ReconfigurableDoubleGauge) jenkinsMeter.gaugeBuilder("memory.used").build();
        DoubleGaugeMock memoryLongGaugeImpl = (DoubleGaugeMock) memoryUsedGauge.getDelegate();
        assertEquals(meterProviderImpl_1.id, memoryLongGaugeImpl.meterProviderId);

        // Observable Double Gauge
        ReconfigurableMeterProvider.ReconfigurableObservableDoubleGauge temperatureGauge = (ReconfigurableMeterProvider.ReconfigurableObservableDoubleGauge) jenkinsMeter.gaugeBuilder("temperature").buildWithCallback(observableDoubleGaugeMeasurement -> observableDoubleGaugeMeasurement.record(random.nextDouble()));
        ObservableDoubleGaugeMock temperatureGaugeImpl = (ObservableDoubleGaugeMock) temperatureGauge.delegate;
        assertEquals(meterProviderImpl_1.id, temperatureGaugeImpl.meterProviderId);

        // Observable Double Measurement
        ReconfigurableMeterProvider.ReconfigurableObservableDoubleMeasurement pressureMeasurement = (ReconfigurableMeterProvider.ReconfigurableObservableDoubleMeasurement) jenkinsMeter.gaugeBuilder("pressure").buildObserver();
        ObservableDoubleMeasurementMock pressureMeasurementImpl = (ObservableDoubleMeasurementMock) pressureMeasurement.getDelegate();
        assertEquals(meterProviderImpl_1.id, pressureMeasurementImpl.meterProviderId);


        // Long Gauge
        ReconfigurableMeterProvider.ReconfigurableLongGauge longGauge = (ReconfigurableMeterProvider.ReconfigurableLongGauge) jenkinsMeter.gaugeBuilder("long.gauge").ofLongs().build();
        LongGaugeMock longGaugeImpl = (LongGaugeMock) longGauge.getDelegate();
        assertEquals(meterProviderImpl_1.id, longGaugeImpl.meterProviderId);

        // Observable Long Gauge
        ReconfigurableMeterProvider.ReconfigurableObservableLongGauge observableLongGauge = (ReconfigurableMeterProvider.ReconfigurableObservableLongGauge) jenkinsMeter.gaugeBuilder("observable.long.gauge").ofLongs().buildWithCallback(observableLongMeasurement -> observableLongMeasurement.record(random.nextInt(5)));
        ObservableLongGaugeMock observableLongGaugeImpl = (ObservableLongGaugeMock) observableLongGauge.delegate;
        assertEquals(meterProviderImpl_1.id, observableLongGaugeImpl.meterProviderId);

        // Observable Long Measurement
        ReconfigurableMeterProvider.ReconfigurableObservableLongMeasurement observableLongGaugeMeasurement = (ReconfigurableMeterProvider.ReconfigurableObservableLongMeasurement) jenkinsMeter.gaugeBuilder("observable.long.measurement").ofLongs().buildObserver();
        ObservableLongMeasurementMock observableLongMeasurementImpl = (ObservableLongMeasurementMock) observableLongGaugeMeasurement.getDelegate();
        assertEquals(meterProviderImpl_1.id, observableLongMeasurementImpl.meterProviderId);

        // ############################################################################################################
        // CHANGE THE IMPLEMENTATION OF THE EVENT METER PROVIDER
        ReconfigurableMeterProviderTest.MeterProviderMock meterProviderImpl_2 = new ReconfigurableMeterProviderTest.MeterProviderMock();
        assertNotEquals(meterProviderImpl_1.id, meterProviderImpl_2.id);

        meterProvider.setDelegate(meterProviderImpl_2);

        // VERIFY THE DELEGATE IMPL HAS CHANGED WHILE THE PARAMS REMAINS UNCHANGED
        ReconfigurableMeterProviderTest.MeterMock jenkinsMeterImpl_2 = (ReconfigurableMeterProviderTest.MeterMock) jenkinsMeter.delegate;
        assertEquals("io.jenkins", jenkinsMeterImpl_2.instrumentationScopeName);
        assertNull(jenkinsMeterImpl_2.schemaUrl);
        assertEquals("1.0.0", jenkinsMeterImpl_2.instrumentationVersion);
        assertEquals(meterProviderImpl_2.id, jenkinsMeterImpl_2.meterProviderId);

        ReconfigurableMeterProviderTest.MeterMock myFrameworkMeterImpl_2 = (ReconfigurableMeterProviderTest.MeterMock) myFrameworkMeter.delegate;

        assertEquals("io.myframework", myFrameworkMeterImpl_2.instrumentationScopeName);
        assertEquals("https://myframework.io/", myFrameworkMeterImpl_2.schemaUrl);
        assertNull(myFrameworkMeterImpl_2.instrumentationVersion);
        assertEquals(meterProviderImpl_2.id, myFrameworkMeterImpl_2.meterProviderId);

        // #### COUNTER ####

        // Long Counter
        assertEquals(meterProviderImpl_2.id, ((LongCounterMock) jenkinsBuildCounter.getDelegate()).meterProviderId);
        // Observable Long Counter
        assertEquals(meterProviderImpl_2.id, ((ObservableLongCounterMock) availableExecutorObservableCounter.delegate).meterProviderId);
        // Observable Long Measurement
        assertEquals(meterProviderImpl_2.id, ((ObservableLongMeasurementMock) busyExecutorObservableMeasurement.getDelegate()).meterProviderId);

        // Double Counter
        assertEquals(meterProviderImpl_2.id, ((DoubleCounterMock) jenkinsBuildDurationCounter.getDelegate()).meterProviderId);
        // Observable Double Counter
        assertEquals(meterProviderImpl_2.id, ((ObservableDoubleCounterMock) observableDoubleCounter.getDelegate()).meterProviderId);
        // Observable Double Measurement
        assertEquals(meterProviderImpl_2.id, ((ObservableDoubleMeasurementMock) observableDoubleMeasurement.getDelegate()).meterProviderId);

        // GAUGE

        // Double Gauge
        assertEquals(meterProviderImpl_2.id, ((DoubleGaugeMock) memoryUsedGauge.getDelegate()).meterProviderId);
        // Observable Double Gauge
        assertEquals(meterProviderImpl_2.id, ((ObservableDoubleGaugeMock) temperatureGauge.delegate).meterProviderId);
        // Observable Double Measurement
        assertEquals(meterProviderImpl_2.id, ((ObservableDoubleMeasurementMock) pressureMeasurement.getDelegate()).meterProviderId);

        // Long Gauge
        assertEquals(meterProviderImpl_2.id, ((LongGaugeMock) longGauge.getDelegate()).meterProviderId);
        // Observable Long Gauge
        assertEquals(meterProviderImpl_2.id, ((ObservableLongGaugeMock) observableLongGauge.delegate).meterProviderId);
        // Observable Long Measurement
        assertEquals(meterProviderImpl_2.id, ((ObservableLongMeasurementMock) observableLongGaugeMeasurement.getDelegate()).meterProviderId);
    }


    static class MeterProviderMock implements MeterProvider {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String id;

        public MeterProviderMock() {
            this.id = "MeterProviderMock-" + ID_SOURCE.incrementAndGet();
        }

        @Override
        public MeterBuilder meterBuilder(String instrumentationScopeName) {
            return new ReconfigurableMeterProviderTest.MeterBuilderMock(instrumentationScopeName, id);
        }

        @Override
        public Meter get(String instrumentationScopeName) {
            return new ReconfigurableMeterProviderTest.MeterMock(instrumentationScopeName, id);
        }
    }

    static class MeterMock implements Meter {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);

        final String instrumentationScopeName;
        final String meterProviderId;
        final String id;

        final String schemaUrl;
        final String instrumentationVersion;

        public MeterMock(String instrumentationScopeName, String meterProviderId) {
            this(instrumentationScopeName, meterProviderId, null, null);
        }

        public MeterMock(String instrumentationScopeName, String instrumentationVersion, String meterProviderId) {
            this(instrumentationScopeName, meterProviderId, null, instrumentationVersion);
        }

        public MeterMock(String instrumentationScopeName, String meterProviderId, @Nullable String schemaUrl, @Nullable String instrumentationVersion) {
            this.id = "MeterMock-" + ID_SOURCE.incrementAndGet();
            this.instrumentationScopeName = instrumentationScopeName;
            this.meterProviderId = meterProviderId;
            this.schemaUrl = schemaUrl;
            this.instrumentationVersion = instrumentationVersion;
        }

        @Override
        public LongCounterBuilder counterBuilder(String name) {
            return new LongCounterBuilderMock(id, meterProviderId);
        }

        @Override
        public LongUpDownCounterBuilder upDownCounterBuilder(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DoubleHistogramBuilder histogramBuilder(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DoubleGaugeBuilder gaugeBuilder(String name) {
            return new DoubleGaugeBuilderMock(id, meterProviderId);
        }

        @Override
        public BatchCallback batchCallback(Runnable callback, ObservableMeasurement observableMeasurement, ObservableMeasurement... additionalMeasurements) {
            throw new UnsupportedOperationException();
        }
    }

    static class MeterBuilderMock implements MeterBuilder {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String id;
        final String instrumentationScopeName;
        final String meterProviderId;
        String schemaUrl;
        String instrumentationVersion;


        public MeterBuilderMock(String instrumentationScopeName, String meterProviderId) {
            this.id = "MeterBuilderMock-" + ID_SOURCE.incrementAndGet();
            this.instrumentationScopeName = instrumentationScopeName;
            this.meterProviderId = meterProviderId;
        }

        @Override
        public MeterBuilder setSchemaUrl(String schemaUrl) {
            this.schemaUrl = schemaUrl;
            return this;
        }

        @Override
        public MeterBuilder setInstrumentationVersion(String instrumentationVersion) {
            this.instrumentationVersion = instrumentationVersion;
            return this;
        }

        @Override
        public Meter build() {
            return new ReconfigurableMeterProviderTest.MeterMock(instrumentationScopeName, meterProviderId, schemaUrl, instrumentationVersion);
        }
    }

    static class LongCounterBuilderMock implements LongCounterBuilder {
        final String meterId;
        final String meterProviderId;
        String description;
        String unit;

        public LongCounterBuilderMock(String meterId, String meterProviderId) {
            this.meterId = meterId;
            this.meterProviderId = meterProviderId;
        }

        @Override
        public LongCounterBuilder setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public LongCounterBuilder setUnit(String unit) {
            this.unit = unit;
            return this;
        }

        @Override
        public DoubleCounterBuilder ofDoubles() {
            DoubleCounterBuilderMock doubleCounterBuilderMock = new DoubleCounterBuilderMock(meterId, meterProviderId);
            Optional.ofNullable(description).ifPresent(doubleCounterBuilderMock::setDescription);
            Optional.ofNullable(unit).ifPresent(doubleCounterBuilderMock::setUnit);
            return doubleCounterBuilderMock;
        }

        @Override
        public ObservableLongCounter buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
            return new ReconfigurableMeterProviderTest.ObservableLongCounterMock(meterId, meterProviderId);
        }

        @Override
        public ObservableLongMeasurement buildObserver() {
            return new ReconfigurableMeterProviderTest.ObservableLongMeasurementMock(meterId, meterProviderId);
        }

        @Override
        public LongCounter build() {
            return new ReconfigurableMeterProviderTest.LongCounterMock(meterId, meterProviderId);
        }
    }

    static class ObservableLongCounterMock implements ObservableLongCounter {
        final String meterProviderId;
        final String meterId;

        public ObservableLongCounterMock(String meterId, String meterProviderId) {
            this.meterId = meterId;
            this.meterProviderId = meterProviderId;
        }
    }

    static class DoubleCounterBuilderMock implements DoubleCounterBuilder {
        final String meterId;
        final String meterProviderId;
        String description;
        String unit;

        public DoubleCounterBuilderMock(String meterId, String meterProviderId) {
            this.meterId = meterId;
            this.meterProviderId = meterProviderId;
        }

        @Override
        public DoubleCounterBuilder setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public DoubleCounterBuilder setUnit(String unit) {
            this.unit = unit;
            return this;
        }

        @Override
        public ObservableDoubleCounter buildWithCallback(Consumer<ObservableDoubleMeasurement> callback) {
            return new ObservableDoubleCounterMock(meterId, meterProviderId);
        }

        @Override
        public ObservableDoubleMeasurement buildObserver() {
            return new ObservableDoubleMeasurementMock(meterId, meterProviderId);
        }

        @Override
        public DoubleCounter build() {
            return new ReconfigurableMeterProviderTest.DoubleCounterMock(meterId, meterProviderId);
        }

    }

    static class ObservableDoubleCounterMock implements ObservableDoubleCounter {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String meterProviderId;
        final String meterId;
        final String id;

        public ObservableDoubleCounterMock(String meterId, String meterProviderId) {
            this.id = "ObservableDoubleCounterMock-" + ID_SOURCE.incrementAndGet();
            this.meterId = meterId;
            this.meterProviderId = meterProviderId;
        }

        @Override
        public void close() {
            ObservableDoubleCounter.super.close();
        }
    }

    static class DoubleCounterMock implements DoubleCounter {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String meterProviderId;
        final String meterId;
        final String id;

        public DoubleCounterMock(String meterId, String meterProviderId) {
            this.id = "DoubleCounterMock-" + ID_SOURCE.incrementAndGet();
            this.meterId = meterId;
            this.meterProviderId = meterProviderId;
        }

        @Override
        public void add(double value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(double value, Attributes attributes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(double value, Attributes attributes, Context context) {
            throw new UnsupportedOperationException();
        }
    }

    static class LongCounterMock implements LongCounter {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String meterProviderId;
        final String meterId;
        final String id;

        public LongCounterMock(String meterId, String meterProviderId) {
            this.id = "LongCounterMock-" + ID_SOURCE.incrementAndGet();
            this.meterId = meterId;
            this.meterProviderId = meterProviderId;
        }

        @Override
        public void add(long value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(long value, Attributes attributes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(long value, Attributes attributes, Context context) {
            throw new UnsupportedOperationException();
        }
    }

    static class LongGaugeMock implements LongGauge {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String meterProviderId;
        final String meterId;
        final String id;

        public LongGaugeMock(String meterId, String meterProviderId) {
            this.id = "LongCounterMock-" + ID_SOURCE.incrementAndGet();
            this.meterId = meterId;
            this.meterProviderId = meterProviderId;
        }

        @Override
        public void set(long value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(long value, Attributes attributes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(long value, Attributes attributes, Context context) {
            throw new UnsupportedOperationException();
        }
    }

    private static class ObservableLongMeasurementMock implements ObservableLongMeasurement {
        final String meterProviderId;
        final String meterId;

        public ObservableLongMeasurementMock(String meterId, String meterProviderId) {
            this.meterId = meterId;
            this.meterProviderId = meterProviderId;
        }

        @Override
        public void record(long value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void record(long value, Attributes attributes) {
            throw new UnsupportedOperationException();
        }
    }

    private static class ObservableDoubleMeasurementMock implements ObservableDoubleMeasurement {
        final String meterProviderId;
        final String meterId;

        public ObservableDoubleMeasurementMock(String meterId, String meterProviderId) {
            this.meterId = meterId;
            this.meterProviderId = meterProviderId;
        }

        @Override
        public void record(double value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void record(double value, Attributes attributes) {
            throw new UnsupportedOperationException();
        }
    }

    static class DoubleGaugeBuilderMock implements DoubleGaugeBuilder {
        final String meterId;
        final String meterProviderId;
        String description;
        String unit;

        public DoubleGaugeBuilderMock(String meterId, String meterProviderId) {
            this.meterId = meterId;
            this.meterProviderId = meterProviderId;
        }

        @Override
        public DoubleGaugeBuilder setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public DoubleGaugeBuilder setUnit(String unit) {
            this.unit = unit;
            return this;
        }

        @Override
        public ObservableDoubleGauge buildWithCallback(Consumer<ObservableDoubleMeasurement> callback) {
            return new ObservableDoubleGaugeMock(meterId, meterProviderId);
        }

        @Override
        public ObservableDoubleMeasurement buildObserver() {
            return new ObservableDoubleMeasurementMock(meterId, meterProviderId);
        }

        @Override
        public DoubleGauge build() {
            return new ReconfigurableMeterProviderTest.DoubleGaugeMock(meterId, meterProviderId);
        }

        @Override
        public LongGaugeBuilder ofLongs() {
            return new LongGaugeBuilderMock(meterId, meterProviderId);
        }
    }

    static class LongGaugeBuilderMock implements LongGaugeBuilder {
        final String meterId;
        final String meterProviderId;
        String description;
        String unit;

        public LongGaugeBuilderMock(String meterId, String meterProviderId) {
            this.meterId = meterId;
            this.meterProviderId = meterProviderId;
        }

        @Override
        public LongGaugeBuilder setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public LongGaugeBuilder setUnit(String unit) {
            this.unit = unit;
            return this;
        }

        @Override
        public ObservableLongGauge buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
            return new ObservableLongGaugeMock(meterId, meterProviderId);
        }

        @Override
        public ObservableLongMeasurement buildObserver() {
            return new ObservableLongMeasurementMock(meterId, meterProviderId);
        }

        @Override
        public LongGauge build() {
            return new LongGaugeMock(meterId, meterProviderId);
        }
    }

    static class ObservableLongGaugeMock implements ObservableLongGauge {
        final String meterProviderId;
        final String meterId;

        public ObservableLongGaugeMock(String meterId, String meterProviderId) {
            this.meterId = meterId;
            this.meterProviderId = meterProviderId;
        }
    }

    static class ObservableDoubleGaugeMock implements ObservableDoubleGauge {
        final String meterProviderId;
        final String meterId;

        public ObservableDoubleGaugeMock(String meterId, String meterProviderId) {
            this.meterId = meterId;
            this.meterProviderId = meterProviderId;
        }
    }

    static class DoubleGaugeMock implements DoubleGauge {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String meterProviderId;
        final String meterId;
        final String id;

        public DoubleGaugeMock(String meterId, String meterProviderId) {
            this.id = "DoubleGaugeMock-" + ID_SOURCE.incrementAndGet();
            this.meterId = meterId;
            this.meterProviderId = meterProviderId;
        }

        @Override
        public void set(double value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(double value, Attributes attributes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(double value, Attributes attributes, Context context) {
            throw new UnsupportedOperationException();
        }
    }
}