/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

/**
 * Interface for components that want to be notified when the Otel SDK has been initialized or will be shutdown.
 * <p>
 * The life cycle of consumers of the OpenTelemetry SDK (consumers of {@link io.opentelemetry.api.trace.TracerProvider},
 * {@link io.opentelemetry.api.metrics.MeterProvider}, and {@link io.opentelemetry.sdk.logs.SdkLoggerProvider}) can NOT
 * use the Jenkins life cycle because those consumers of the Otel SDK need to perform initialization tasks after the
 * Otel SDK has been initialized and have to shut down things before the Otel SDK is shutdown due to a reconfiguration.
 * <p>
 * Used by components that create counters...
 */
public interface OpenTelemetryLifecycleListener extends Comparable<OpenTelemetryLifecycleListener>{

    /**
     * Invoked soon after the Otel SDK has been initialized.
     * Created {@link AutoCloseable} metering instruments don't have to be closed by Otel components, the OpenTelemetry
     * plugin takes care of this  (eg {@link ObservableLongUpDownCounter}, {@link ObservableLongCounter}...)
     *
     * @param meter            {@link Meter} of the newly initialized Otel SDK
     * @param loggerProvider   {@link io.opentelemetry.api.logs.Logger} of the newly initialized Otel SDK
     * @param eventEmitter
     * @param tracer           {@link Tracer} of the newly initialized Otel SDK
     * @param configProperties {@link ConfigProperties} of the newly initialized Otel SDK
     */
    default void afterSdkInitialized(Meter meter, LoggerProvider loggerProvider, EventEmitter eventEmitter, Tracer tracer, ConfigProperties configProperties) {}

    /**
     * Invoked soon after the Otel SDK has been initialized.
     * Created {@link AutoCloseable} metering instruments don't have to be closed by Otel components, the OpenTelemetry
     * plugin takes care of this  (eg {@link ObservableLongUpDownCounter}, {@link ObservableLongCounter}...)
     *
     * @param openTelemetry
     * @param configProperties {@link ConfigProperties} of the newly initialized Otel SDK
     */
    default void afterSdkInitialized(OpenTelemetry openTelemetry, ConfigProperties configProperties) {}

    /**
     * Invoked just before the Otel SDK is shutdown.
     * Created {@link AutoCloseable} metering instruments don't have to be closed by Otel components, the OpenTelemetry
     * plugin takes care of this  (eg {@link ObservableLongUpDownCounter}, {@link ObservableLongCounter}...)
     */
    default void beforeSdkShutdown() {}

    /**
     * @return the ordinal of this otel component to execute step handlers in predictable order. The smallest ordinal is handled first.
     */
    default int ordinal() {
        return 0;
    }

    @Override
    default int compareTo(OpenTelemetryLifecycleListener other) {
        if (this.ordinal() == other.ordinal()) {
            return this.getClass().getName().compareTo(other.getClass().getName());
        } else {
            return Integer.compare(this.ordinal(), other.ordinal());
        }
    }
}
