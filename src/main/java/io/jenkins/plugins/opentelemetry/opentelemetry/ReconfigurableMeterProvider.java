/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
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

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * <p>
 * A {@link MeterProvider} that allows to reconfigure the {@link Meter}s.
 * </p>
 * <p>
 * We need reconfigurability because Jenkins supports changing the configuration of the OpenTelemetry params at runtime.
 * All instantiated meters are reconfigured when the configuration changes, when
 * {@link ReconfigurableMeterProvider#setDelegate(MeterProvider)} is invoked.
 * </p>
 */
class ReconfigurableMeterProvider implements MeterProvider {

    private MeterProvider delegate;

    private final ConcurrentMap<InstrumentationScope, ReconfigurableMeter> meters = new ConcurrentHashMap<>();

    public ReconfigurableMeterProvider() {
        this(MeterProvider.noop());
    }

    public ReconfigurableMeterProvider(MeterProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized Meter get(String instrumentationScopeName) {
        return meters.computeIfAbsent(
            new InstrumentationScope(instrumentationScopeName),
            instrumentationScope -> new ReconfigurableMeter(delegate.get(instrumentationScope.instrumentationScopeName)));
    }

    public synchronized void setDelegate(MeterProvider delegate) {
        this.delegate = delegate;
        meters.forEach((instrumentationScope, reconfigurableMeter) -> {
            MeterBuilder meterBuilder = delegate.meterBuilder(instrumentationScope.instrumentationScopeName);
            Optional.ofNullable(instrumentationScope.instrumentationScopeVersion).ifPresent(meterBuilder::setInstrumentationVersion);
            Optional.ofNullable(instrumentationScope.schemaUrl).ifPresent(meterBuilder::setSchemaUrl);
            reconfigurableMeter.setDelegate(meterBuilder.build());
        });
    }

    @Override
    public MeterBuilder meterBuilder(String instrumentationScopeName) {
        return new ReconfigurableMeterBuilder(delegate.meterBuilder(instrumentationScopeName), instrumentationScopeName);
    }

    public synchronized MeterProvider getDelegate() {
        return delegate;
    }


    @VisibleForTesting
    protected class ReconfigurableMeterBuilder implements MeterBuilder {
        MeterBuilder delegate;
        String instrumentationScopeName;
        String schemaUrl;
        String instrumentationScopeVersion;

        public ReconfigurableMeterBuilder(MeterBuilder delegate, String instrumentationScopeName) {
            this.delegate = Objects.requireNonNull(delegate);
            this.instrumentationScopeName = Objects.requireNonNull(instrumentationScopeName);
        }

        @Override
        public MeterBuilder setSchemaUrl(String schemaUrl) {
            delegate.setSchemaUrl(schemaUrl);
            this.schemaUrl = schemaUrl;
            return this;
        }

        @Override
        public MeterBuilder setInstrumentationVersion(String instrumentationScopeVersion) {
            delegate.setInstrumentationVersion(instrumentationScopeVersion);
            this.instrumentationScopeVersion = instrumentationScopeVersion;
            return this;
        }

        @Override
        public Meter build() {
            InstrumentationScope instrumentationScope = new InstrumentationScope(instrumentationScopeName, schemaUrl, instrumentationScopeVersion);
            return meters.computeIfAbsent(instrumentationScope, k -> new ReconfigurableMeter(delegate.build()));
        }
    }

    static class InstrumentKey {
        final String name;
        @Nullable
        final String description;
        @Nullable
        final String unit;

        public InstrumentKey(String name, @Nullable String description, @Nullable String unit) {
            this.name = name;
            this.description = description;
            this.unit = unit;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InstrumentKey that = (InstrumentKey) o;
            return Objects.equals(name, that.name) && Objects.equals(description, that.description) && Objects.equals(unit, that.unit);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, description, unit);
        }
    }

    static class ObservableLongMeasurementCallbackKey {
        final String name;
        @Nullable
        final String description;
        @Nullable
        final String unit;
        final Consumer<ObservableLongMeasurement> callback;

        public ObservableLongMeasurementCallbackKey(String name, @Nullable String description, @Nullable String unit, Consumer<ObservableLongMeasurement> callback) {
            this.name = name;
            this.description = description;
            this.unit = unit;
            this.callback = callback;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ObservableLongMeasurementCallbackKey that = (ObservableLongMeasurementCallbackKey) o;
            return Objects.equals(name, that.name) && Objects.equals(description, that.description) && Objects.equals(unit, that.unit) && Objects.equals(callback, that.callback);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, description, unit, callback);
        }
    }

    static class ObservableDoubleMeasurementCallbackKey {
        final String name;
        @Nullable
        final String description;
        @Nullable
        final String unit;
        final Consumer<ObservableDoubleMeasurement> callback;

        public ObservableDoubleMeasurementCallbackKey(String name, @Nullable String description, @Nullable String unit, Consumer<ObservableDoubleMeasurement> callback) {
            this.name = name;
            this.description = description;
            this.unit = unit;
            this.callback = callback;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ObservableDoubleMeasurementCallbackKey that = (ObservableDoubleMeasurementCallbackKey) o;
            return Objects.equals(name, that.name) && Objects.equals(description, that.description) && Objects.equals(unit, that.unit) && Objects.equals(callback, that.callback);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, description, unit, callback);
        }
    }

    @VisibleForTesting
    protected class ReconfigurableMeter implements Meter {
        Meter delegate;
        // long counters
        final ConcurrentMap<InstrumentKey, ReconfigurableLongCounter> longCounters = new ConcurrentHashMap<>();
        final ConcurrentMap<ObservableLongMeasurementCallbackKey, ReconfigurableObservableLongCounter> observableLongCounters = new ConcurrentHashMap<>();
        // double counters
        final ConcurrentMap<InstrumentKey, ReconfigurableDoubleCounter> doubleCounters = new ConcurrentHashMap<>();
        final ConcurrentMap<InstrumentKey, ReconfigurableObservableLongMeasurement> observableLongCounterMeasurements = new ConcurrentHashMap<>();

        // Long gauges
        final ConcurrentMap<InstrumentKey, ReconfigurableLongGauge> longGauges = new ConcurrentHashMap<>();
        final ConcurrentMap<ObservableLongMeasurementCallbackKey, ReconfigurableObservableLongGauge> observableLongGauges = new ConcurrentHashMap<>();
        final ConcurrentMap<InstrumentKey, ReconfigurableObservableLongMeasurement> observableLongGaugeMeasurements = new ConcurrentHashMap<>();
        // double gauges
        final ConcurrentMap<InstrumentKey, ReconfigurableDoubleGauge> doubleGauges = new ConcurrentHashMap<>();
        final ConcurrentMap<ObservableDoubleMeasurementCallbackKey, ReconfigurableObservableDoubleGauge> observableDoubleGauges = new ConcurrentHashMap<>();
        final ConcurrentMap<InstrumentKey, ReconfigurableObservableDoubleMeasurement> observableDoubleGaugeMeasurements = new ConcurrentHashMap<>();

        public ReconfigurableMeter(Meter delegate) {
            this.delegate = delegate;
        }

        @Override
        public BatchCallback batchCallback(Runnable callback, ObservableMeasurement observableMeasurement, ObservableMeasurement... additionalMeasurements) {
            return Meter.super.batchCallback(callback, observableMeasurement, additionalMeasurements);
        }

        @Override
        public DoubleGaugeBuilder gaugeBuilder(String name) {
            return new ReconfigurableDoubleGaugeBuilder(delegate.gaugeBuilder(name), name,
                doubleGauges, observableDoubleGauges, observableDoubleGaugeMeasurements,
                longGauges, observableLongGauges, observableLongGaugeMeasurements);
        }

        @Override
        public DoubleHistogramBuilder histogramBuilder(String name) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public LongUpDownCounterBuilder upDownCounterBuilder(String name) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public LongCounterBuilder counterBuilder(String name) {
            return new ReconfigurableLongCounterBuilder(delegate.counterBuilder(name), name, longCounters, doubleCounters, observableLongCounters, observableLongCounterMeasurements);
        }

        public synchronized void setDelegate(Meter delegate) {
            this.delegate = delegate;
            // Long counters
            this.longCounters.forEach((counterKey, reconfigurableLongCounter) -> {
                LongCounterBuilder longCounterBuilder = delegate.counterBuilder(counterKey.name);
                Optional.ofNullable(counterKey.description).ifPresent(longCounterBuilder::setDescription);
                Optional.ofNullable(counterKey.unit).ifPresent(longCounterBuilder::setUnit);
                reconfigurableLongCounter.setDelegate(longCounterBuilder.build());
            });
            this.observableLongCounters.forEach((callbackKey, reconfigurableObservableLongCounter) -> {
                LongCounterBuilder longCounterBuilder = delegate.counterBuilder(callbackKey.name);
                Optional.ofNullable(callbackKey.description).ifPresent(longCounterBuilder::setDescription);
                Optional.ofNullable(callbackKey.unit).ifPresent(longCounterBuilder::setUnit);
                reconfigurableObservableLongCounter.setDelegate(longCounterBuilder.buildWithCallback(callbackKey.callback));
            });
            this.observableLongCounterMeasurements.forEach((counterKey, reconfigurableObservableLongMeasurement) -> {
                LongCounterBuilder longCounterBuilder = delegate.counterBuilder(counterKey.name);
                Optional.ofNullable(counterKey.description).ifPresent(longCounterBuilder::setDescription);
                Optional.ofNullable(counterKey.unit).ifPresent(longCounterBuilder::setUnit);
                reconfigurableObservableLongMeasurement.setDelegate(longCounterBuilder.buildObserver());
            });

            // Double counters
            this.doubleCounters.forEach((counterKey, reconfigurableDoubleCounter) -> {
                DoubleCounterBuilder doubleCounterBuilder = delegate.counterBuilder(counterKey.name).ofDoubles();
                Optional.ofNullable(counterKey.description).ifPresent(doubleCounterBuilder::setDescription);
                Optional.ofNullable(counterKey.unit).ifPresent(doubleCounterBuilder::setUnit);
                reconfigurableDoubleCounter.setDelegate(doubleCounterBuilder.build());
            });

            // Double gauges
            this.doubleGauges.forEach((counterKey, reconfigurableDoubleGauge) -> {
                DoubleGaugeBuilder doubleGaugeBuilder = delegate.gaugeBuilder(counterKey.name);
                Optional.ofNullable(counterKey.description).ifPresent(doubleGaugeBuilder::setDescription);
                Optional.ofNullable(counterKey.unit).ifPresent(doubleGaugeBuilder::setUnit);
                reconfigurableDoubleGauge.setDelegate(doubleGaugeBuilder.build());
            });
            this.observableDoubleGauges.forEach((callbackKey, reconfigurableObservableDoubleGauge) -> {
                DoubleGaugeBuilder doubleGaugeBuilder = delegate.gaugeBuilder(callbackKey.name);
                Optional.ofNullable(callbackKey.description).ifPresent(doubleGaugeBuilder::setDescription);
                Optional.ofNullable(callbackKey.unit).ifPresent(doubleGaugeBuilder::setUnit);
                reconfigurableObservableDoubleGauge.setDelegate(doubleGaugeBuilder.buildWithCallback(callbackKey.callback));
            });
            this.observableDoubleGaugeMeasurements.forEach((counterKey, reconfigurableObservableDoubleMeasurement) -> {
                DoubleGaugeBuilder doubleGaugeBuilder = delegate.gaugeBuilder(counterKey.name);
                Optional.ofNullable(counterKey.description).ifPresent(doubleGaugeBuilder::setDescription);
                Optional.ofNullable(counterKey.unit).ifPresent(doubleGaugeBuilder::setUnit);
                reconfigurableObservableDoubleMeasurement.setDelegate(doubleGaugeBuilder.buildObserver());
            });

            // Long gauges
            this.longGauges.forEach((counterKey, reconfigurableLongGauge) -> {
                LongGaugeBuilder longGaugeBuilder = delegate.gaugeBuilder(counterKey.name).ofLongs();
                Optional.ofNullable(counterKey.description).ifPresent(longGaugeBuilder::setDescription);
                Optional.ofNullable(counterKey.unit).ifPresent(longGaugeBuilder::setUnit);
                reconfigurableLongGauge.setDelegate(longGaugeBuilder.build());
            });
            this.observableLongGauges.forEach((callbackKey, reconfigurableObservableLongGauge) -> {
                LongGaugeBuilder longGaugeBuilder = delegate.gaugeBuilder(callbackKey.name).ofLongs();
                Optional.ofNullable(callbackKey.description).ifPresent(longGaugeBuilder::setDescription);
                Optional.ofNullable(callbackKey.unit).ifPresent(longGaugeBuilder::setUnit);
                reconfigurableObservableLongGauge.setDelegate(longGaugeBuilder.buildWithCallback(callbackKey.callback));
            });
            this.observableLongGaugeMeasurements.forEach((counterKey, reconfigurableObservableLongMeasurement) -> {
                LongGaugeBuilder longGaugeBuilder = delegate.gaugeBuilder(counterKey.name).ofLongs();
                Optional.ofNullable(counterKey.description).ifPresent(longGaugeBuilder::setDescription);
                Optional.ofNullable(counterKey.unit).ifPresent(longGaugeBuilder::setUnit);
                reconfigurableObservableLongMeasurement.setDelegate(longGaugeBuilder.buildObserver());
            });

        }

        public synchronized Meter getDelegate() {
            return delegate;
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableObservableLongMeasurement implements ObservableLongMeasurement {
        private ObservableLongMeasurement delegate;

        ReconfigurableObservableLongMeasurement(ObservableLongMeasurement delegate) {
            this.delegate = delegate;
        }

        @Override
        public void record(long value) {
            delegate.record(value);
        }

        @Override
        public void record(long value, Attributes attributes) {
            delegate.record(value, attributes);
        }

        public ObservableLongMeasurement getDelegate() {
            return delegate;
        }

        public void setDelegate(ObservableLongMeasurement delegate) {
            this.delegate = delegate;
        }
    }

    static class ReconfigurableLongCounterBuilder implements LongCounterBuilder {
        LongCounterBuilder delegate;
        final ConcurrentMap<InstrumentKey, ReconfigurableLongCounter> longCounters;
        final ConcurrentMap<InstrumentKey, ReconfigurableDoubleCounter> doubleCounters;
        final ConcurrentMap<ObservableLongMeasurementCallbackKey, ReconfigurableObservableLongCounter> observableLongCounters;
        final ConcurrentMap<InstrumentKey, ReconfigurableObservableLongMeasurement> observableLongMeasurements;

        final String name;
        String description;
        String unit;


        ReconfigurableLongCounterBuilder(LongCounterBuilder delegate, String name,
                                         ConcurrentMap<InstrumentKey, ReconfigurableLongCounter> longCounters,
                                         ConcurrentMap<InstrumentKey, ReconfigurableDoubleCounter> doubleCounters,
                                         ConcurrentMap<ObservableLongMeasurementCallbackKey, ReconfigurableObservableLongCounter> observableLongCounters,
                                         ConcurrentMap<InstrumentKey, ReconfigurableObservableLongMeasurement> observableLongMeasurements) {
            this.delegate = delegate;
            this.name = name;
            this.longCounters = longCounters;
            this.doubleCounters = doubleCounters;
            this.observableLongCounters = observableLongCounters;
            this.observableLongMeasurements = observableLongMeasurements;
        }

        @Override
        public LongCounterBuilder setDescription(String description) {
            delegate.setDescription(description);
            this.description = description;
            return this;
        }

        @Override
        public LongCounterBuilder setUnit(String unit) {
            delegate.setUnit(unit);
            this.unit = unit;
            return this;
        }

        @Override
        public DoubleCounterBuilder ofDoubles() {
            ReconfigurableDoubleCounterBuilder reconfigurableDoubleCounterBuilder = new ReconfigurableDoubleCounterBuilder(delegate.ofDoubles(), name, doubleCounters);
            Optional.ofNullable(description).ifPresent(reconfigurableDoubleCounterBuilder::setDescription);
            Optional.ofNullable(unit).ifPresent(reconfigurableDoubleCounterBuilder::setUnit);
            return reconfigurableDoubleCounterBuilder;
        }

        @Override
        public LongCounter build() {
            InstrumentKey counterKey = new InstrumentKey(name, description, unit);
            return longCounters.computeIfAbsent(counterKey, k -> new ReconfigurableLongCounter(delegate.build()));
        }

        @Override
        public ObservableLongCounter buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
            ObservableLongMeasurementCallbackKey key = new ObservableLongMeasurementCallbackKey(name, description, unit, callback);
            return this.observableLongCounters.computeIfAbsent(key, k -> new ReconfigurableObservableLongCounter(delegate.buildWithCallback(callback)));
        }

        @Override
        public ObservableLongMeasurement buildObserver() {
            InstrumentKey counterKey = new InstrumentKey(name, description, unit);
            return this.observableLongMeasurements.computeIfAbsent(counterKey, k -> new ReconfigurableObservableLongMeasurement(delegate.buildObserver()));
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableLongCounter implements LongCounter {
        private LongCounter delegate;

        ReconfigurableLongCounter(LongCounter delegate) {
            this.delegate = delegate;
        }

        @Override
        public void add(long increment) {
            delegate.add(increment);
        }

        @Override
        public void add(long value, Attributes attributes) {
            delegate.add(value, attributes);
        }

        @Override
        public void add(long value, Attributes attributes, Context context) {
            delegate.add(value, attributes, context);
        }

        public void setDelegate(LongCounter delegate) {
            this.delegate = delegate;
        }

        public LongCounter getDelegate() {
            return delegate;
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableObservableLongCounter implements ObservableLongCounter {
        ObservableLongCounter delegate;

        ReconfigurableObservableLongCounter(ObservableLongCounter delegate) {
            this.delegate = delegate;
        }

        public void setDelegate(ObservableLongCounter delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            delegate.close();
        }

    }

    static class ReconfigurableDoubleCounterBuilder implements DoubleCounterBuilder {
        DoubleCounterBuilder delegate;
        final ConcurrentMap<InstrumentKey, ReconfigurableDoubleCounter> doubleCounters;
        final String name;
        String description;
        String unit;

        ReconfigurableDoubleCounterBuilder(DoubleCounterBuilder delegate, String name, ConcurrentMap<InstrumentKey, ReconfigurableDoubleCounter> doubleCounters) {
            this.delegate = delegate;
            this.name = name;
            this.doubleCounters = doubleCounters;
        }

        @Override
        public DoubleCounterBuilder setDescription(String description) {
            delegate.setDescription(description);
            this.description = description;
            return this;
        }

        @Override
        public DoubleCounterBuilder setUnit(String unit) {
            delegate.setUnit(unit);
            this.unit = unit;
            return this;
        }

        @Override
        public DoubleCounter build() {
            InstrumentKey counterKey = new InstrumentKey(name, description, unit);
            return doubleCounters.computeIfAbsent(counterKey, k -> new ReconfigurableDoubleCounter(delegate.build()));
        }

        @Override
        public ObservableDoubleCounter buildWithCallback(Consumer<ObservableDoubleMeasurement> callback) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public ObservableDoubleMeasurement buildObserver() {
            throw new UnsupportedOperationException("Not implemented");
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableDoubleCounter implements DoubleCounter {
        private DoubleCounter delegate;

        ReconfigurableDoubleCounter(DoubleCounter delegate) {
            this.delegate = delegate;
        }

        @Override
        public void add(double increment) {
            delegate.add(increment);
        }

        @Override
        public void add(double value, Attributes attributes) {
            delegate.add(value, attributes);
        }

        @Override
        public void add(double value, Attributes attributes, Context context) {
            delegate.add(value, attributes, context);
        }

        public void setDelegate(DoubleCounter delegate) {
            this.delegate = delegate;
        }

        public DoubleCounter getDelegate() {
            return delegate;
        }
    }


    static class ReconfigurableDoubleGaugeBuilder implements DoubleGaugeBuilder {
        DoubleGaugeBuilder delegate;
        final ConcurrentMap<InstrumentKey, ReconfigurableDoubleGauge> doubleGauges;
        final ConcurrentMap<ObservableDoubleMeasurementCallbackKey, ReconfigurableObservableDoubleGauge> observableDoubleGauges;
        final ConcurrentMap<InstrumentKey, ReconfigurableObservableDoubleMeasurement> observableDoubleMeasurements;
        final ConcurrentMap<InstrumentKey, ReconfigurableLongGauge> longGauges;
        final ConcurrentMap<ObservableLongMeasurementCallbackKey, ReconfigurableObservableLongGauge> observableLongGauges;
        final ConcurrentMap<InstrumentKey, ReconfigurableObservableLongMeasurement> observableLongMeasurements;


        final String name;
        String description;
        String unit;

        ReconfigurableDoubleGaugeBuilder(
            DoubleGaugeBuilder delegate, String name, ConcurrentMap<InstrumentKey,
            ReconfigurableDoubleGauge> doubleGauges,
            ConcurrentMap<ObservableDoubleMeasurementCallbackKey, ReconfigurableObservableDoubleGauge> observableDoubleGauges, ConcurrentMap<InstrumentKey, ReconfigurableObservableDoubleMeasurement> observableDoubleMeasurements, ConcurrentMap<InstrumentKey, ReconfigurableLongGauge> longGauges, ConcurrentMap<ObservableLongMeasurementCallbackKey, ReconfigurableObservableLongGauge> observableLongGauges,
            ConcurrentMap<InstrumentKey, ReconfigurableObservableLongMeasurement> observableLongMeasurements) {

            this.delegate = delegate;
            this.name = name;
            this.doubleGauges = doubleGauges;
            this.observableDoubleGauges = observableDoubleGauges;
            this.observableDoubleMeasurements = observableDoubleMeasurements;
            this.observableLongGauges = observableLongGauges;
            this.observableLongMeasurements = observableLongMeasurements;
            this.longGauges = longGauges;
        }

        @Override
        public DoubleGaugeBuilder setDescription(String description) {
            delegate.setDescription(description);
            this.description = description;
            return this;
        }

        @Override
        public DoubleGaugeBuilder setUnit(String unit) {
            delegate.setUnit(unit);
            this.unit = unit;
            return this;
        }

        @Override
        public LongGaugeBuilder ofLongs() {
            ReconfigurableLongGaugeBuilder reconfigurableLongCounterBuilder = new ReconfigurableLongGaugeBuilder(delegate.ofLongs(), name, longGauges, observableLongGauges, observableLongMeasurements);
            Optional.ofNullable(description).ifPresent(reconfigurableLongCounterBuilder::setDescription);
            Optional.ofNullable(unit).ifPresent(reconfigurableLongCounterBuilder::setUnit);
            return reconfigurableLongCounterBuilder;
        }

        @Override
        public DoubleGauge build() {
            InstrumentKey gaugeKey = new InstrumentKey(name, description, unit);
            return doubleGauges.computeIfAbsent(gaugeKey, k -> new ReconfigurableDoubleGauge(delegate.build()));
        }

        @Override
        public ObservableDoubleGauge buildWithCallback(Consumer<ObservableDoubleMeasurement> callback) {
            ObservableDoubleMeasurementCallbackKey key = new ObservableDoubleMeasurementCallbackKey(name, description, unit, callback);
            return this.observableDoubleGauges.computeIfAbsent(key, k -> new ReconfigurableObservableDoubleGauge(delegate.buildWithCallback(callback)));
        }

        @Override
        public ObservableDoubleMeasurement buildObserver() {
            InstrumentKey gaugeKey = new InstrumentKey(name, description, unit);
            return this.observableDoubleMeasurements.computeIfAbsent(gaugeKey, k -> new ReconfigurableObservableDoubleMeasurement(delegate.buildObserver()));
        }

    }

    static class ReconfigurableLongGaugeBuilder implements LongGaugeBuilder {
        LongGaugeBuilder delegate;
        final ConcurrentMap<InstrumentKey, ReconfigurableLongGauge> longGauges;
        final ConcurrentMap<ObservableLongMeasurementCallbackKey, ReconfigurableObservableLongGauge> observableLongGauges;
        final ConcurrentMap<InstrumentKey, ReconfigurableObservableLongMeasurement> observableLongMeasurements;

        final String name;
        String description;
        String unit;


        ReconfigurableLongGaugeBuilder(LongGaugeBuilder delegate, String name,
                                       ConcurrentMap<InstrumentKey, ReconfigurableLongGauge> longGauges,
                                       ConcurrentMap<ObservableLongMeasurementCallbackKey, ReconfigurableObservableLongGauge> observableLongGauges,
                                       ConcurrentMap<InstrumentKey, ReconfigurableObservableLongMeasurement> observableLongMeasurements) {
            this.delegate = delegate;
            this.name = name;
            this.longGauges = longGauges;
            this.observableLongGauges = observableLongGauges;
            this.observableLongMeasurements = observableLongMeasurements;
        }

        @Override
        public LongGaugeBuilder setDescription(String description) {
            delegate.setDescription(description);
            this.description = description;
            return this;
        }

        @Override
        public LongGaugeBuilder setUnit(String unit) {
            delegate.setUnit(unit);
            this.unit = unit;
            return this;
        }

        @Override
        public LongGauge build() {
            InstrumentKey counterKey = new InstrumentKey(name, description, unit);
            return longGauges.computeIfAbsent(counterKey, k -> new ReconfigurableLongGauge(delegate.build()));
        }

        @Override
        public ObservableLongGauge buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
            ObservableLongMeasurementCallbackKey key = new ObservableLongMeasurementCallbackKey(name, description, unit, callback);
            return this.observableLongGauges.computeIfAbsent(key, k -> new ReconfigurableObservableLongGauge(delegate.buildWithCallback(callback)));
        }

        @Override
        public ObservableLongMeasurement buildObserver() {
            InstrumentKey counterKey = new InstrumentKey(name, description, unit);
            return this.observableLongMeasurements.computeIfAbsent(counterKey, k -> new ReconfigurableObservableLongMeasurement(delegate.buildObserver()));
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableLongGauge implements LongGauge {
        private LongGauge delegate;

        ReconfigurableLongGauge(LongGauge delegate) {
            this.delegate = delegate;
        }

        @Override
        public void set(long value) {
            delegate.set(value);
        }

        @Override
        public void set(long value, Attributes attributes) {
            delegate.set(value, attributes);
        }

        @Override
        public void set(long value, Attributes attributes, Context context) {
            delegate.set(value, attributes, context);
        }

        public void setDelegate(LongGauge delegate) {
            this.delegate = delegate;
        }

        public LongGauge getDelegate() {
            return delegate;
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableObservableLongGauge implements ObservableLongGauge {
        ObservableLongGauge delegate;

        ReconfigurableObservableLongGauge(ObservableLongGauge delegate) {
            this.delegate = delegate;
        }

        public void setDelegate(ObservableLongGauge delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableDoubleGauge implements DoubleGauge {
        private DoubleGauge delegate;

        ReconfigurableDoubleGauge(DoubleGauge delegate) {
            this.delegate = delegate;
        }

        @Override
        public void set(double value) {
            delegate.set(value);
        }

        @Override
        public void set(double value, Attributes attributes) {
            delegate.set(value, attributes);
        }

        @Override
        public void set(double value, Attributes attributes, Context context) {
            delegate.set(value, attributes, context);
        }

        public void setDelegate(DoubleGauge delegate) {
            this.delegate = delegate;
        }

        public DoubleGauge getDelegate() {
            return delegate;
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableObservableDoubleGauge implements ObservableDoubleGauge {
        ObservableDoubleGauge delegate;

        ReconfigurableObservableDoubleGauge(ObservableDoubleGauge delegate) {
            this.delegate = delegate;
        }

        public void setDelegate(ObservableDoubleGauge delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableObservableDoubleMeasurement implements ObservableDoubleMeasurement {
        private ObservableDoubleMeasurement delegate;

        ReconfigurableObservableDoubleMeasurement(ObservableDoubleMeasurement delegate) {
            this.delegate = delegate;
        }

        @Override
        public void record(double value) {
            delegate.record(value);
        }

        @Override
        public void record(double value, Attributes attributes) {
            delegate.record(value, attributes);
        }

        public ObservableDoubleMeasurement getDelegate() {
            return delegate;
        }

        public void setDelegate(ObservableDoubleMeasurement delegate) {
            this.delegate = delegate;
        }
    }
}
