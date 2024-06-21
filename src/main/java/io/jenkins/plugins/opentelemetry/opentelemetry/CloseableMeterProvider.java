/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.ObservableDoubleCounter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.api.metrics.ObservableMeasurement;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link MeterProvider} that can be closed before the underlying {@link io.opentelemetry.sdk.OpenTelemetrySdk}
 * is shut down.
 */
class CloseableMeterProvider implements MeterProvider, Closeable {
    private final static Logger LOGGER = Logger.getLogger(CloseableMeterProvider.class.getName());

    /**
     * Reference on all the instantiated {@link AutoCloseable} instrument in order to properly close them before
     * reconfigurations (eg {@link ObservableLongUpDownCounter}, {@link ObservableLongCounter}...).
     */
    final List<AutoCloseable> closeables = new ArrayList<>();

    final MeterProvider delegate;

    public CloseableMeterProvider(MeterProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Meter get(String instrumentationScopeName) {
        return new CloseableMeter(delegate.get(instrumentationScopeName));
    }

    @Override
    public MeterBuilder meterBuilder(String instrumentationScopeName) {
        return new CloseableMeterBuilder(delegate.meterBuilder(instrumentationScopeName));
    }

    @Override
    public void close() {
        LOGGER.log(Level.FINE, () -> "Close " + closeables.size() + " instruments");
        LOGGER.log(Level.FINEST, () -> "Close " + closeables);
        List<AutoCloseable> instruments = new ArrayList<>(this.closeables);
        this.closeables.clear(); // reset the list of instruments for reuse

        for (AutoCloseable instrument : instruments) {
            try {
                instrument.close();
            } catch (Exception e) {
                // should never happen, Otel instruments override the #close method to indicate they don't throw exceptions
                LOGGER.log(Level.WARNING, "Exception closing " + instrument, e);
            }
        }
    }

    class CloseableMeterBuilder implements MeterBuilder {
        final MeterBuilder delegate;

        CloseableMeterBuilder(MeterBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        public MeterBuilder setSchemaUrl(String schemaUrl) {
            delegate.setSchemaUrl(schemaUrl);
            return this;
        }

        @Override
        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        public MeterBuilder setInstrumentationVersion(String instrumentationScopeVersion) {
            delegate.setInstrumentationVersion(instrumentationScopeVersion);
            return this;
        }

        @Override
        public Meter build() {
            return new CloseableMeter(delegate.build());
        }
    }

    class CloseableMeter implements Meter {
        private final Meter delegate;

        CloseableMeter(Meter delegate) {
            this.delegate = delegate;
        }

        @Override
        public LongCounterBuilder counterBuilder(String name) {
            return new CloseableLongCounterBuilder(delegate.counterBuilder(name));
        }

        @Override
        public LongUpDownCounterBuilder upDownCounterBuilder(String name) {
            return new CloseableLongUpDownCounterBuilder(delegate.upDownCounterBuilder(name));
        }

        @Override
        public DoubleHistogramBuilder histogramBuilder(String name) {
            return delegate.histogramBuilder(name);
        }

        @Override
        public DoubleGaugeBuilder gaugeBuilder(String name) {
            return new CloseableDoubleGaugeBuilder(delegate.gaugeBuilder(name));
        }

        @Override
        public BatchCallback batchCallback(Runnable callback, ObservableMeasurement observableMeasurement, ObservableMeasurement... additionalMeasurements) {
            BatchCallback batchCallback = delegate.batchCallback(callback, observableMeasurement, additionalMeasurements);
            closeables.add(batchCallback);
            return batchCallback;
        }
    }

    class CloseableLongCounterBuilder implements LongCounterBuilder {
        final LongCounterBuilder delegate;

        CloseableLongCounterBuilder(LongCounterBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        public LongCounterBuilder setDescription(String description) {
            delegate.setDescription(description);
            return this;
        }

        @Override
        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        public LongCounterBuilder setUnit(String unit) {
            delegate.setUnit(unit);
            return this;
        }

        @Override
        public DoubleCounterBuilder ofDoubles() {
            return new CloseableDoubleCounterBuilder(delegate.ofDoubles());
        }

        @Override
        public LongCounter build() {
            return delegate.build();
        }

        @Override
        public ObservableLongCounter buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
            ObservableLongCounter observableLongCounter = delegate.buildWithCallback(callback);
            closeables.add(observableLongCounter);
            return observableLongCounter;
        }

        @Override
        public ObservableLongMeasurement buildObserver() {
            return delegate.buildObserver();
        }
    }

    class CloseableLongUpDownCounterBuilder implements LongUpDownCounterBuilder {
        @Override
        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        public LongUpDownCounterBuilder setDescription(String description) {
            delegate.setDescription(description);
            return this;
        }

        @Override
        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        public LongUpDownCounterBuilder setUnit(String unit) {
            delegate.setUnit(unit);
            return this;
        }

        @Override
        public DoubleUpDownCounterBuilder ofDoubles() {
            return new CloseableDoubleUpDownCounterBuilder(delegate.ofDoubles());
        }

        @Override
        public LongUpDownCounter build() {
            return delegate.build();
        }

        @Override
        public ObservableLongUpDownCounter buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
            ObservableLongUpDownCounter observableLongUpDownCounter = delegate.buildWithCallback(callback);
            closeables.add(observableLongUpDownCounter);
            return observableLongUpDownCounter;
        }

        @Override
        public ObservableLongMeasurement buildObserver() {
            return delegate.buildObserver();
        }

        final LongUpDownCounterBuilder delegate;

        CloseableLongUpDownCounterBuilder(LongUpDownCounterBuilder delegate) {
            this.delegate = delegate;
        }
    }


    class CloseableDoubleUpDownCounterBuilder implements DoubleUpDownCounterBuilder {
        final DoubleUpDownCounterBuilder delegate;

        CloseableDoubleUpDownCounterBuilder(DoubleUpDownCounterBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        public DoubleUpDownCounterBuilder setDescription(String description) {
            delegate.setDescription(description);
            return this;
        }

        @Override
        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        public DoubleUpDownCounterBuilder setUnit(String unit) {
            delegate.setUnit(unit);
            return this;
        }

        @Override
        public DoubleUpDownCounter build() {
            return delegate.build();
        }

        @Override
        public ObservableDoubleUpDownCounter buildWithCallback(Consumer<ObservableDoubleMeasurement> callback) {
            ObservableDoubleUpDownCounter observableDoubleUpDownCounter = delegate.buildWithCallback(callback);
            closeables.add(observableDoubleUpDownCounter);
            return observableDoubleUpDownCounter;
        }

        @Override
        public ObservableDoubleMeasurement buildObserver() {
            return delegate.buildObserver();
        }
    }

    class CloseableDoubleGaugeBuilder implements DoubleGaugeBuilder {
        final DoubleGaugeBuilder delegate;

        CloseableDoubleGaugeBuilder(DoubleGaugeBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        public DoubleGaugeBuilder setDescription(String description) {
            delegate.setDescription(description);
            return this;
        }

        @Override
        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        public DoubleGaugeBuilder setUnit(String unit) {
            delegate.setUnit(unit);
            return this;
        }

        @Override
        public LongGaugeBuilder ofLongs() {
            return new CloseableLongGaugeBuilder(delegate.ofLongs());
        }

        @Override
        public ObservableDoubleGauge buildWithCallback(Consumer<ObservableDoubleMeasurement> callback) {
            ObservableDoubleGauge observableDoubleGauge = delegate.buildWithCallback(callback);
            closeables.add(observableDoubleGauge);
            return observableDoubleGauge;
        }

        @Override
        public ObservableDoubleMeasurement buildObserver() {
            return delegate.buildObserver();
        }
    }

    class CloseableLongGaugeBuilder implements LongGaugeBuilder {
        final LongGaugeBuilder delegate;

        CloseableLongGaugeBuilder(LongGaugeBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        public LongGaugeBuilder setDescription(String description) {
            delegate.setDescription(description);
            return this;
        }

        @Override
        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        public LongGaugeBuilder setUnit(String unit) {
            delegate.setUnit(unit);
            return this;
        }

        @Override
        public ObservableLongGauge buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
            ObservableLongGauge observableLongGauge = delegate.buildWithCallback(callback);
            closeables.add(observableLongGauge);
            return observableLongGauge;
        }

        @Override
        public ObservableLongMeasurement buildObserver() {
            return delegate.buildObserver();
        }
    }

    class CloseableDoubleCounterBuilder implements DoubleCounterBuilder {
        final DoubleCounterBuilder delegate;

        CloseableDoubleCounterBuilder(DoubleCounterBuilder delegate) {
            this.delegate = delegate;
        }

        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        @Override
        public DoubleCounterBuilder setDescription(String description) {
            delegate.setDescription(description);
            return this;
        }

        @Override
        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        public DoubleCounterBuilder setUnit(String unit) {
            delegate.setUnit(unit);
            return this;
        }

        @Override
        public DoubleCounter build() {
            return delegate.build();
        }

        @Override
        public ObservableDoubleCounter buildWithCallback(Consumer<ObservableDoubleMeasurement> callback) {
            ObservableDoubleCounter observableDoubleCounter = delegate.buildWithCallback(callback);
            closeables.add(observableDoubleCounter);
            return observableDoubleCounter;
        }

        @Override
        public ObservableDoubleMeasurement buildObserver() {
            return delegate.buildObserver();
        }
    }
}