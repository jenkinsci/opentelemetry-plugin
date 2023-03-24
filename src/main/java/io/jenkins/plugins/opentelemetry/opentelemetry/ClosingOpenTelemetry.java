/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.*;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;

import javax.annotation.CheckReturnValue;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds a reference on all the instantiated {@link AutoCloseable} instrument in order to properly close them before
 * reconfigurations (eg {@link ObservableLongUpDownCounter}, {@link ObservableLongCounter}...).
 */
public class ClosingOpenTelemetry implements OpenTelemetry, Closeable {

    private final static Logger LOGGER = Logger.getLogger(ClosingOpenTelemetry.class.getName());

    public static ClosingOpenTelemetry noop() {
        return new ClosingOpenTelemetry(OpenTelemetry.noop());
    }

    final OpenTelemetry delegate;

    final List<AutoCloseable> instruments = new ArrayList<>();

    @Override
    public void close() {
        List<AutoCloseable> instruments = new ArrayList<>(this.instruments);
        this.instruments.clear(); // reset the list of instruments for reuse
        LOGGER.log(Level.FINE, () -> "Close " + instruments.size() + " instruments");
        LOGGER.log(Level.FINEST, () -> "Close " + instruments);
        for (AutoCloseable instrument : instruments) {
            try {
                instrument.close();
            } catch (Exception e) {
                // should never happen, Otel instruments override the #close method to indicate they don't throw exceptions
                LOGGER.log(Level.WARNING, "Exception closing " + instrument, e);
            }
        }
    }

    public ClosingOpenTelemetry(OpenTelemetry delegate) {
        this.delegate = delegate;
    }

    @Override
    public TracerProvider getTracerProvider() {
        return delegate.getTracerProvider();
    }

    @Override
    public Tracer getTracer(String instrumentationScopeName) {
        return delegate.getTracer(instrumentationScopeName);
    }

    @Override
    public Tracer getTracer(String instrumentationScopeName, String instrumentationScopeVersion) {
        return delegate.getTracer(instrumentationScopeName, instrumentationScopeVersion);
    }

    @Override
    public TracerBuilder tracerBuilder(String instrumentationScopeName) {
        return delegate.tracerBuilder(instrumentationScopeName);
    }

    @Override
    public MeterProvider getMeterProvider() {
        return new ClosingMeterProvider(delegate.getMeterProvider());
    }

    @Override
    public Meter getMeter(String instrumentationScopeName) {
        return new ClosingMeter(delegate.getMeter(instrumentationScopeName));
    }

    @Override
    public MeterBuilder meterBuilder(String instrumentationScopeName) {
        return new ClosingMeterBuilder(delegate.meterBuilder(instrumentationScopeName));
    }

    @Override
    public ContextPropagators getPropagators() {
        return delegate.getPropagators();
    }

    class ClosingMeterProvider implements MeterProvider {
        final MeterProvider delegate;

        public ClosingMeterProvider(MeterProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public Meter get(String instrumentationScopeName) {
            return new ClosingMeter(delegate.get(instrumentationScopeName));
        }

        @Override
        public MeterBuilder meterBuilder(String instrumentationScopeName) {
            return new ClosingMeterBuilder(delegate.meterBuilder(instrumentationScopeName));
        }
    }

    class ClosingMeterBuilder implements MeterBuilder {
        final MeterBuilder delegate;

        ClosingMeterBuilder(MeterBuilder delegate) {
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
            return new ClosingMeter(delegate.build());
        }
    }

    class ClosingMeter implements Meter {
        private final Meter delegate;

        ClosingMeter(Meter delegate) {
            this.delegate = delegate;
        }

        @Override
        public LongCounterBuilder counterBuilder(String name) {
            return new ClosingLongCounterBuilder(delegate.counterBuilder(name));
        }

        @Override
        public LongUpDownCounterBuilder upDownCounterBuilder(String name) {
            return new ClosingLongUpDownCounterBuilder(delegate.upDownCounterBuilder(name));
        }

        @Override
        public DoubleHistogramBuilder histogramBuilder(String name) {
            return delegate.histogramBuilder(name);
        }

        @Override
        public DoubleGaugeBuilder gaugeBuilder(String name) {
            return new ClosingDoubleGaugeBuilder(delegate.gaugeBuilder(name));
        }

        @Override
        public BatchCallback batchCallback(Runnable callback, ObservableMeasurement observableMeasurement, ObservableMeasurement... additionalMeasurements) {
            return delegate.batchCallback(callback, observableMeasurement, additionalMeasurements);
        }
    }

    class ClosingLongCounterBuilder implements LongCounterBuilder {
        final LongCounterBuilder delegate;

        ClosingLongCounterBuilder(LongCounterBuilder delegate) {
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
            return new ClosingDoubleCounterBuilder(delegate.ofDoubles());
        }

        @Override
        public LongCounter build() {
            return delegate.build();
        }

        @Override
        public ObservableLongCounter buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
            ObservableLongCounter observableLongCounter = delegate.buildWithCallback(callback);
            instruments.add(observableLongCounter);
            return observableLongCounter;
        }

        @Override
        public ObservableLongMeasurement buildObserver() {
            return delegate.buildObserver();
        }
    }

    class ClosingLongUpDownCounterBuilder implements LongUpDownCounterBuilder {
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
            return new ClosingDoubleUpDownCounterBuilder(delegate.ofDoubles());
        }

        @Override
        public LongUpDownCounter build() {
            return delegate.build();
        }

        @Override
        public ObservableLongUpDownCounter buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
            ObservableLongUpDownCounter observableLongUpDownCounter = delegate.buildWithCallback(callback);
            instruments.add(observableLongUpDownCounter);
            return observableLongUpDownCounter;
        }

        @Override
        public ObservableLongMeasurement buildObserver() {
            return delegate.buildObserver();
        }

        final LongUpDownCounterBuilder delegate;

        ClosingLongUpDownCounterBuilder(LongUpDownCounterBuilder delegate) {
            this.delegate = delegate;
        }
    }


    class ClosingDoubleUpDownCounterBuilder implements DoubleUpDownCounterBuilder {
        final DoubleUpDownCounterBuilder delegate;

        ClosingDoubleUpDownCounterBuilder(DoubleUpDownCounterBuilder delegate) {
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
            instruments.add(observableDoubleUpDownCounter);
            return observableDoubleUpDownCounter;
        }

        @Override
        public ObservableDoubleMeasurement buildObserver() {
            return delegate.buildObserver();
        }
    }

    class ClosingDoubleGaugeBuilder implements DoubleGaugeBuilder {
        final DoubleGaugeBuilder delegate;

        ClosingDoubleGaugeBuilder(DoubleGaugeBuilder delegate) {
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
            return new ClosingLongGaugeBuilder(delegate.ofLongs());
        }

        @Override
        public ObservableDoubleGauge buildWithCallback(Consumer<ObservableDoubleMeasurement> callback) {
            ObservableDoubleGauge observableDoubleGauge = delegate.buildWithCallback(callback);
            instruments.add(observableDoubleGauge);
            return observableDoubleGauge;
        }

        @Override
        public ObservableDoubleMeasurement buildObserver() {
            return delegate.buildObserver();
        }
    }

    class ClosingLongGaugeBuilder implements LongGaugeBuilder {
        final LongGaugeBuilder delegate;

        ClosingLongGaugeBuilder(LongGaugeBuilder delegate) {
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
            instruments.add(observableLongGauge);
            return observableLongGauge;
        }

        @Override
        public ObservableLongMeasurement buildObserver() {
            return delegate.buildObserver();
        }
    }

    class ClosingDoubleCounterBuilder implements DoubleCounterBuilder {
        final DoubleCounterBuilder delegate;

        ClosingDoubleCounterBuilder(DoubleCounterBuilder delegate) {
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
            instruments.add(observableDoubleCounter);
            return observableDoubleCounter;
        }

        @Override
        public ObservableDoubleMeasurement buildObserver() {
            return delegate.buildObserver();
        }
    }
}
