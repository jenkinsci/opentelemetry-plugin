/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.events.EventLoggerProvider;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;

public abstract class AbstractOpenTelemetryWrapper implements OpenTelemetry {

    protected abstract OpenTelemetry getOpenTelemetryDelegate();

    protected abstract EventLoggerProvider getEventLoggerProvider();
    
    @Override
    public TracerProvider getTracerProvider() {
        return getOpenTelemetryDelegate().getTracerProvider();
    }

    @Override
    public Tracer getTracer(String instrumentationScopeName) {
        return getOpenTelemetryDelegate().getTracer(instrumentationScopeName);
    }

    @Override
    public Tracer getTracer(String instrumentationScopeName, String instrumentationScopeVersion) {
        return getOpenTelemetryDelegate().getTracer(instrumentationScopeName, instrumentationScopeVersion);
    }

    @Override
    public TracerBuilder tracerBuilder(String instrumentationScopeName) {
        return getOpenTelemetryDelegate().tracerBuilder(instrumentationScopeName);
    }

    @Override
    public MeterProvider getMeterProvider() {
        return getOpenTelemetryDelegate().getMeterProvider();
    }

    @Override
    public Meter getMeter(String instrumentationScopeName) {
        return getOpenTelemetryDelegate().getMeter(instrumentationScopeName);
    }

    @Override
    public MeterBuilder meterBuilder(String instrumentationScopeName) {
        return getOpenTelemetryDelegate().meterBuilder(instrumentationScopeName);
    }

    @Override
    public LoggerProvider getLogsBridge() {
        return getOpenTelemetryDelegate().getLogsBridge();
    }

    @Override
    public ContextPropagators getPropagators() {
        return getOpenTelemetryDelegate().getPropagators();
    }
}
