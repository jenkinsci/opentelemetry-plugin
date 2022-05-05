/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.logs.LogEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Interface for components that want to be notified when the Otel SDK has been initialized or will be shutdown.
 *
 * Used by components that create counters...
 */
public interface OtelComponent {

    /**
     * Invoked soon after the Otel SDK has been initialized.
     *
     * @param meter {@link Meter} of the newly initialized Otel SDK
     * @param logEmitter {@link LogEmitter} of the newly initialized Otel SDK
     * @param tracer {@link Tracer} of the newly initialized Otel SDK
     * @param configProperties {@link ConfigProperties} of the newly initialized Otel SDK
     */
    void afterSdkInitialized(Meter meter, LogEmitter logEmitter, Tracer tracer, ConfigProperties configProperties);

    /**
     * Invoked just before the Otel SDK is shutdown
     */
    void beforeSdkShutdown();

    /**
     * Helper for {@link OtelComponent} implementations to manage the created metric instruments
     */
    class State {
        private final static Logger logger = Logger.getLogger(State.class.getName());
        private final List<AutoCloseable> instruments = new ArrayList<>();

        public void registerInstrument(ObservableLongCounter instrument) {
            instruments.add(instrument);
        }

        public void registerInstrument(ObservableLongGauge instrument) {
            instruments.add(instrument);
        }

        public void registerInstrument(ObservableLongUpDownCounter instrument) {
            instruments.add(instrument);
        }

        public void registerInstrument(ObservableDoubleGauge instrument) {
            instruments.add(instrument);
        }

        public void closeInstruments() {
            List<AutoCloseable> instruments = this.instruments;
            this.instruments.clear(); // reset the list of instruments for reuse
            for (AutoCloseable instrument : instruments) {
                try {
                    instrument.close();
                } catch (Exception e) {
                    // should never happen, Otel instruments override the #close method to indicate they don't throw exceptions
                    logger.log(Level.INFO, "Exception closing instrument " + instrument, e);
                }
            }
        }
    }
}
