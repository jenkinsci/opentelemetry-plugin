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
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
@ThreadSafe
class ReconfigurableMeterProvider implements MeterProvider {

    @GuardedBy("lock")
    private MeterProvider delegate;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final ConcurrentMap<InstrumentationScope, ReconfigurableMeter> meters = new ConcurrentHashMap<>();

    public ReconfigurableMeterProvider() {
        this(MeterProvider.noop());
    }

    public ReconfigurableMeterProvider(MeterProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Meter get(String instrumentationScopeName) {
        lock.readLock().lock();
        try {
            return meters.computeIfAbsent(
                new InstrumentationScope(instrumentationScopeName),
                instrumentationScope -> new ReconfigurableMeter(delegate.get(instrumentationScope.instrumentationScopeName), lock));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setDelegate(MeterProvider delegate) {
        lock.writeLock().lock();
        try {
            this.delegate = delegate;
            meters.forEach((instrumentationScope, reconfigurableMeter) -> {
                MeterBuilder meterBuilder = delegate.meterBuilder(instrumentationScope.instrumentationScopeName);
                Optional.ofNullable(instrumentationScope.instrumentationScopeVersion).ifPresent(meterBuilder::setInstrumentationVersion);
                Optional.ofNullable(instrumentationScope.schemaUrl).ifPresent(meterBuilder::setSchemaUrl);
                reconfigurableMeter.setDelegate(meterBuilder.build());
            });
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public MeterBuilder meterBuilder(String instrumentationScopeName) {
        lock.readLock().lock();
        try {
            return new ReconfigurableMeterBuilder(delegate.meterBuilder(instrumentationScopeName), instrumentationScopeName, lock);
        } finally {
            lock.readLock().unlock();
        }
    }

    public MeterProvider getDelegate() {
        lock.readLock().lock();
        try {
            return delegate;
        } finally {
            lock.readLock().unlock();
        }
    }


    @VisibleForTesting
    protected class ReconfigurableMeterBuilder implements MeterBuilder {
        final ReadWriteLock lock;
        MeterBuilder delegate;
        String instrumentationScopeName;
        String schemaUrl;
        String instrumentationScopeVersion;

        public ReconfigurableMeterBuilder(MeterBuilder delegate, String instrumentationScopeName, ReadWriteLock lock) {
            this.delegate = Objects.requireNonNull(delegate);
            this.instrumentationScopeName = Objects.requireNonNull(instrumentationScopeName);
            this.lock = lock;
        }

        @Override
        public MeterBuilder setSchemaUrl(String schemaUrl) {
            lock.readLock().lock();
            try {
                delegate.setSchemaUrl(schemaUrl);
                this.schemaUrl = schemaUrl;
                return this;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public MeterBuilder setInstrumentationVersion(String instrumentationScopeVersion) {
            lock.readLock().lock();
            try {
                delegate.setInstrumentationVersion(instrumentationScopeVersion);
                this.instrumentationScopeVersion = instrumentationScopeVersion;
                return this;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public Meter build() {
            lock.readLock().lock();
            try {
                InstrumentationScope instrumentationScope = new InstrumentationScope(instrumentationScopeName, schemaUrl, instrumentationScopeVersion);
                return meters.computeIfAbsent(instrumentationScope, k -> new ReconfigurableMeter(delegate.build(), lock));
            } finally {
                lock.readLock().unlock();
            }
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

    @ThreadSafe
    @VisibleForTesting
    protected static class ReconfigurableMeter implements Meter {
        final ReadWriteLock lock;
        @GuardedBy("lock")
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

        public ReconfigurableMeter(Meter delegate, ReadWriteLock lock) {
            this.delegate = delegate;
            this.lock = lock;
        }

        @Override
        public BatchCallback batchCallback(Runnable callback, ObservableMeasurement observableMeasurement, ObservableMeasurement... additionalMeasurements) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public DoubleGaugeBuilder gaugeBuilder(String name) {
            lock.readLock().lock();
            try {
                return new ReconfigurableDoubleGaugeBuilder(delegate.gaugeBuilder(name), name,
                    doubleGauges, observableDoubleGauges, observableDoubleGaugeMeasurements,
                    longGauges, observableLongGauges, observableLongGaugeMeasurements,
                    lock);
            } finally {
                lock.readLock().unlock();
            }
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
            lock.readLock().lock();
            try {
                return new ReconfigurableLongCounterBuilder(delegate.counterBuilder(name), name, longCounters, doubleCounters, observableLongCounters, observableLongCounterMeasurements, lock);
            } finally {
                lock.readLock().unlock();
            }
        }

        public void setDelegate(Meter delegate) {
            lock.writeLock().lock();
            try {
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
            } finally {
                lock.writeLock().unlock();
            }
        }

        public synchronized Meter getDelegate() {
            lock.readLock().lock();
            try {
                return delegate;
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableObservableLongMeasurement implements ObservableLongMeasurement {
        final ReadWriteLock lock;
        private ObservableLongMeasurement delegate;

        ReconfigurableObservableLongMeasurement(ObservableLongMeasurement delegate, ReadWriteLock lock) {
            this.delegate = delegate;
            this.lock = lock;
        }

        @Override
        public void record(long value) {
            lock.readLock().lock();
            try {
                delegate.record(value);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void record(long value, Attributes attributes) {
            lock.readLock().lock();
            try {
                delegate.record(value, attributes);
            } finally {
                lock.readLock().unlock();
            }
        }

        public ObservableLongMeasurement getDelegate() {
            lock.readLock().lock();
            try {
                return delegate;
            } finally {
                lock.readLock().unlock();
            }
        }

        public void setDelegate(ObservableLongMeasurement delegate) {
            lock.writeLock().lock();
            try {
                this.delegate = delegate;
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    static class ReconfigurableLongCounterBuilder implements LongCounterBuilder {
        final ReadWriteLock lock;
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
                                         ConcurrentMap<InstrumentKey, ReconfigurableObservableLongMeasurement> observableLongMeasurements,
                                         ReadWriteLock lock) {
            this.delegate = delegate;
            this.name = name;
            this.longCounters = longCounters;
            this.doubleCounters = doubleCounters;
            this.observableLongCounters = observableLongCounters;
            this.observableLongMeasurements = observableLongMeasurements;

            this.lock = lock;
        }

        @Override
        public LongCounterBuilder setDescription(String description) {
            lock.readLock().lock();
            try {
                delegate.setDescription(description);
                this.description = description;
                return this;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public LongCounterBuilder setUnit(String unit) {
            lock.readLock().lock();
            try {
                delegate.setUnit(unit);
                this.unit = unit;
                return this;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public DoubleCounterBuilder ofDoubles() {
            lock.readLock().lock();
            try {
                ReconfigurableDoubleCounterBuilder reconfigurableDoubleCounterBuilder = new ReconfigurableDoubleCounterBuilder(delegate.ofDoubles(), name, doubleCounters, lock);
                Optional.ofNullable(description).ifPresent(reconfigurableDoubleCounterBuilder::setDescription);
                Optional.ofNullable(unit).ifPresent(reconfigurableDoubleCounterBuilder::setUnit);
                return reconfigurableDoubleCounterBuilder;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public LongCounter build() {
            lock.readLock().lock();
            try {
                InstrumentKey counterKey = new InstrumentKey(name, description, unit);
                return longCounters.computeIfAbsent(counterKey, k -> new ReconfigurableLongCounter(delegate.build(), lock));
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public ObservableLongCounter buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
            lock.readLock().lock();
            try {
                ObservableLongMeasurementCallbackKey key = new ObservableLongMeasurementCallbackKey(name, description, unit, callback);
                return this.observableLongCounters.computeIfAbsent(key, k -> new ReconfigurableObservableLongCounter(delegate.buildWithCallback(callback), lock));
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public ObservableLongMeasurement buildObserver() {
            lock.readLock().lock();
            try {
                InstrumentKey counterKey = new InstrumentKey(name, description, unit);
                return this.observableLongMeasurements.computeIfAbsent(counterKey, k -> new ReconfigurableObservableLongMeasurement(delegate.buildObserver(), lock));
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    @VisibleForTesting
    @ThreadSafe
    protected static class ReconfigurableLongCounter implements LongCounter {
        final ReadWriteLock lock;
        @GuardedBy("lock")
        private LongCounter delegate;

        ReconfigurableLongCounter(LongCounter delegate, ReadWriteLock lock) {
            this.delegate = delegate;
            this.lock = lock;
        }

        @Override
        public void add(long increment) {
            lock.readLock().lock();
            try {
                delegate.add(increment);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void add(long value, Attributes attributes) {
            lock.readLock().lock();
            try {
                delegate.add(value, attributes);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void add(long value, Attributes attributes, Context context) {
            lock.readLock().lock();
            try {
                delegate.add(value, attributes, context);
            } finally {
                lock.readLock().unlock();
            }
        }

        public void setDelegate(LongCounter delegate) {
            lock.writeLock().lock();
            try {
                this.delegate = delegate;
            } finally {
                lock.writeLock().unlock();
            }
        }

        public LongCounter getDelegate() {
            lock.readLock().lock();
            try {
                return delegate;
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableObservableLongCounter implements ObservableLongCounter {
        final ReadWriteLock lock;
        ObservableLongCounter delegate;

        ReconfigurableObservableLongCounter(ObservableLongCounter delegate, ReadWriteLock lock) {
            this.delegate = delegate;
            this.lock = lock;
        }

        public void setDelegate(ObservableLongCounter delegate) {
            lock.writeLock().lock();
            try {
                this.delegate = delegate;
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public void close() {
            lock.readLock().lock();
            try {
                delegate.close();
            } finally {
                lock.readLock().unlock();
            }
        }

    }

    static class ReconfigurableDoubleCounterBuilder implements DoubleCounterBuilder {
        final ReadWriteLock lock;
        DoubleCounterBuilder delegate;
        final ConcurrentMap<InstrumentKey, ReconfigurableDoubleCounter> doubleCounters;
        final String name;
        String description;
        String unit;

        ReconfigurableDoubleCounterBuilder(DoubleCounterBuilder delegate, String name, ConcurrentMap<InstrumentKey, ReconfigurableDoubleCounter> doubleCounters, ReadWriteLock lock) {
            this.delegate = delegate;
            this.name = name;
            this.doubleCounters = doubleCounters;
            this.lock = lock;
        }

        @Override
        public DoubleCounterBuilder setDescription(String description) {
            lock.readLock().lock();
            try {
                delegate.setDescription(description);
                this.description = description;
                return this;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public DoubleCounterBuilder setUnit(String unit) {
            lock.readLock().lock();
            try {
                delegate.setUnit(unit);
                this.unit = unit;
                return this;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public DoubleCounter build() {
            lock.readLock().lock();
            try {
                InstrumentKey counterKey = new InstrumentKey(name, description, unit);
                return doubleCounters.computeIfAbsent(counterKey, k -> new ReconfigurableDoubleCounter(delegate.build(), lock));
            } finally {
                lock.readLock().unlock();
            }
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
    @ThreadSafe
    protected static class ReconfigurableDoubleCounter implements DoubleCounter {
        final ReadWriteLock lock;
        @GuardedBy("lock")
        private DoubleCounter delegate;

        ReconfigurableDoubleCounter(DoubleCounter delegate, ReadWriteLock lock) {
            this.delegate = delegate;
            this.lock = lock;
        }

        @Override
        public void add(double increment) {
            lock.readLock().lock();
            try {
                delegate.add(increment);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void add(double value, Attributes attributes) {
            lock.readLock().lock();
            try {
                delegate.add(value, attributes);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void add(double value, Attributes attributes, Context context) {
            lock.readLock().lock();
            try {
                delegate.add(value, attributes, context);
            } finally {
                lock.readLock().unlock();
            }
        }

        public void setDelegate(DoubleCounter delegate) {
            lock.writeLock().lock();
            try {
                this.delegate = delegate;
            } finally {
                lock.writeLock().unlock();
            }
        }

        public DoubleCounter getDelegate() {
            lock.readLock().lock();
            try {
                return delegate;
            } finally {
                lock.readLock().unlock();
            }
        }
    }


    static class ReconfigurableDoubleGaugeBuilder implements DoubleGaugeBuilder {
        final ReadWriteLock lock;
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
            ConcurrentMap<InstrumentKey, ReconfigurableObservableLongMeasurement> observableLongMeasurements,
            ReadWriteLock lock) {

            this.delegate = delegate;
            this.name = name;
            this.doubleGauges = doubleGauges;
            this.observableDoubleGauges = observableDoubleGauges;
            this.observableDoubleMeasurements = observableDoubleMeasurements;
            this.observableLongGauges = observableLongGauges;
            this.observableLongMeasurements = observableLongMeasurements;
            this.longGauges = longGauges;

            this.lock = lock;
        }

        @Override
        public DoubleGaugeBuilder setDescription(String description) {
            lock.readLock().lock();
            try {
                delegate.setDescription(description);
                this.description = description;
                return this;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public DoubleGaugeBuilder setUnit(String unit) {
            lock.readLock().lock();
            try {
            delegate.setUnit(unit);
            this.unit = unit;
            return this;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public LongGaugeBuilder ofLongs() {
            lock.readLock().lock();
            try {
            ReconfigurableLongGaugeBuilder reconfigurableLongCounterBuilder = new ReconfigurableLongGaugeBuilder(delegate.ofLongs(), name, longGauges, observableLongGauges, observableLongMeasurements, lock);
            Optional.ofNullable(description).ifPresent(reconfigurableLongCounterBuilder::setDescription);
            Optional.ofNullable(unit).ifPresent(reconfigurableLongCounterBuilder::setUnit);
            return reconfigurableLongCounterBuilder;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public DoubleGauge build() {
            lock.readLock().lock();
            try {
            InstrumentKey gaugeKey = new InstrumentKey(name, description, unit);
            return doubleGauges.computeIfAbsent(gaugeKey, k -> new ReconfigurableDoubleGauge(delegate.build(), lock));
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public ObservableDoubleGauge buildWithCallback(Consumer<ObservableDoubleMeasurement> callback) {
            lock.readLock().lock();
            try {
            ObservableDoubleMeasurementCallbackKey key = new ObservableDoubleMeasurementCallbackKey(name, description, unit, callback);
            return this.observableDoubleGauges.computeIfAbsent(key, k -> new ReconfigurableObservableDoubleGauge(delegate.buildWithCallback(callback), lock));
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public ObservableDoubleMeasurement buildObserver() {
            lock.readLock().lock();
            try {
            InstrumentKey gaugeKey = new InstrumentKey(name, description, unit);
            return this.observableDoubleMeasurements.computeIfAbsent(gaugeKey, k -> new ReconfigurableObservableDoubleMeasurement(delegate.buildObserver(), lock));
            } finally {
                lock.readLock().unlock();
            }
        }

    }

    static class ReconfigurableLongGaugeBuilder implements LongGaugeBuilder {
        final ReadWriteLock lock;
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
                                       ConcurrentMap<InstrumentKey, ReconfigurableObservableLongMeasurement> observableLongMeasurements,
                                       ReadWriteLock lock) {
            this.delegate = delegate;
            this.name = name;
            this.longGauges = longGauges;
            this.observableLongGauges = observableLongGauges;
            this.observableLongMeasurements = observableLongMeasurements;

            this.lock = lock;
        }

        @Override
        public LongGaugeBuilder setDescription(String description) {
            lock.readLock().lock();
            try {
                delegate.setDescription(description);
                this.description = description;
                return this;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public LongGaugeBuilder setUnit(String unit) {
            lock.readLock().lock();
            try {
                delegate.setUnit(unit);
                this.unit = unit;
                return this;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public LongGauge build() {
            lock.readLock().lock();
            try {
                InstrumentKey counterKey = new InstrumentKey(name, description, unit);
                return longGauges.computeIfAbsent(counterKey, k -> new ReconfigurableLongGauge(delegate.build(), lock));
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public ObservableLongGauge buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
            lock.readLock().lock();
            try {
                ObservableLongMeasurementCallbackKey key = new ObservableLongMeasurementCallbackKey(name, description, unit, callback);
                return this.observableLongGauges.computeIfAbsent(key, k -> new ReconfigurableObservableLongGauge(delegate.buildWithCallback(callback), lock));
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public ObservableLongMeasurement buildObserver() {
            lock.readLock().lock();
            try {
                InstrumentKey counterKey = new InstrumentKey(name, description, unit);
                return this.observableLongMeasurements.computeIfAbsent(counterKey, k -> new ReconfigurableObservableLongMeasurement(delegate.buildObserver(), lock));
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    @VisibleForTesting
    @ThreadSafe
    protected static class ReconfigurableLongGauge implements LongGauge {
        final ReadWriteLock lock;
        @GuardedBy("lock")
        private LongGauge delegate;

        ReconfigurableLongGauge(LongGauge delegate, ReadWriteLock lock) {
            this.delegate = delegate;
            this.lock = lock;
        }

        @Override
        public void set(long value) {
            lock.readLock().lock();
            try {
                delegate.set(value);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void set(long value, Attributes attributes) {
            lock.readLock().lock();
            try {
                delegate.set(value, attributes);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void set(long value, Attributes attributes, Context context) {
            lock.readLock().lock();
            try {
                delegate.set(value, attributes, context);
            } finally {
                lock.readLock().unlock();
            }
        }

        public void setDelegate(LongGauge delegate) {
            lock.writeLock().lock();
            try {
                this.delegate = delegate;
            } finally {
                lock.writeLock().unlock();
            }
        }

        public LongGauge getDelegate() {
            lock.readLock().lock();
            try {
                return delegate;
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableObservableLongGauge implements ObservableLongGauge {
        final ReadWriteLock lock;
        ObservableLongGauge delegate;

        ReconfigurableObservableLongGauge(ObservableLongGauge delegate, ReadWriteLock lock) {
            this.delegate = delegate;
            this.lock = lock;
        }

        public void setDelegate(ObservableLongGauge delegate) {
            lock.writeLock().lock();
            try {
                this.delegate = delegate;
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public void close() {
            lock.readLock().lock();
            try {
                delegate.close();
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    @VisibleForTesting
    @ThreadSafe
    protected static class ReconfigurableDoubleGauge implements DoubleGauge {
        final ReadWriteLock lock;
        @GuardedBy("lock")
        private DoubleGauge delegate;

        ReconfigurableDoubleGauge(DoubleGauge delegate, ReadWriteLock lock) {
            this.delegate = delegate;
            this.lock = lock;
        }

        @Override
        public void set(double value) {
            lock.readLock().lock();
            try {
                delegate.set(value);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void set(double value, Attributes attributes) {
            lock.readLock().lock();
            try {
                delegate.set(value, attributes);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void set(double value, Attributes attributes, Context context) {
            lock.readLock().lock();
            try {
                delegate.set(value, attributes, context);
            } finally {
                lock.readLock().unlock();
            }
        }

        public void setDelegate(DoubleGauge delegate) {
            lock.writeLock().lock();
            try {
                this.delegate = delegate;
            } finally {
                lock.writeLock().unlock();
            }
        }

        public DoubleGauge getDelegate() {
            lock.readLock().lock();
            try {
                return delegate;
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableObservableDoubleGauge implements ObservableDoubleGauge {
        final ReadWriteLock lock;
        ObservableDoubleGauge delegate;

        ReconfigurableObservableDoubleGauge(ObservableDoubleGauge delegate, ReadWriteLock lock) {
            this.delegate = delegate;
            this.lock = lock;
        }

        public void setDelegate(ObservableDoubleGauge delegate) {
            lock.writeLock().lock();
            try {
                this.delegate = delegate;
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public void close() {
            lock.readLock().lock();
            try {
                delegate.close();
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableObservableDoubleMeasurement implements ObservableDoubleMeasurement {
        final ReadWriteLock lock;
        private ObservableDoubleMeasurement delegate;

        ReconfigurableObservableDoubleMeasurement(ObservableDoubleMeasurement delegate, ReadWriteLock lock) {
            this.delegate = delegate;
            this.lock = lock;
        }

        @Override
        public void record(double value) {
            lock.readLock().lock();
            try {
                delegate.record(value);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void record(double value, Attributes attributes) {
            lock.readLock().lock();
            try {
                delegate.record(value, attributes);
            } finally {
                lock.readLock().unlock();
            }
        }

        public ObservableDoubleMeasurement getDelegate() {
            lock.readLock().lock();
            try {
                return delegate;
            } finally {
                lock.readLock().unlock();
            }
        }

        public void setDelegate(ObservableDoubleMeasurement delegate) {
            lock.writeLock().lock();
            try {
                this.delegate = delegate;
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
}
