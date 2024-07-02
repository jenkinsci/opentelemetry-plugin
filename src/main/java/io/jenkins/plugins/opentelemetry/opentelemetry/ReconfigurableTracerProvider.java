/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * <p>
 * A {@link TracerProvider} that allows to reconfigure the {@link Tracer}s.
 * </p>
 * <p>
 * We need reconfigurability because Jenkins supports changing the configuration of the OpenTelemetry params at runtime.
 * All instantiated tracers are reconfigured when the configuration changes, when
 * {@link ReconfigurableTracerProvider#setDelegate(TracerProvider)} is invoked.
 * </p>
 */
class ReconfigurableTracerProvider implements TracerProvider {

    private TracerProvider delegate;

    private final ConcurrentMap<InstrumentationScope, ReconfigurableTracer> tracers = new ConcurrentHashMap<>();

    public ReconfigurableTracerProvider() {
        this(TracerProvider.noop());
    }

    public ReconfigurableTracerProvider(TracerProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized Tracer get(String instrumentationScopeName) {
        return tracers.computeIfAbsent(
            new InstrumentationScope(instrumentationScopeName),
            instrumentationScope -> new ReconfigurableTracer(delegate.get(instrumentationScope.instrumentationScopeName)));
    }

    public synchronized void setDelegate(TracerProvider delegate) {
        this.delegate = delegate;
        tracers.forEach((instrumentationScope, reconfigurableTracer) -> {
            TracerBuilder tracerBuilder = delegate.tracerBuilder(instrumentationScope.instrumentationScopeName);
            Optional.ofNullable(instrumentationScope.instrumentationScopeVersion).ifPresent(tracerBuilder::setInstrumentationVersion);
            Optional.ofNullable(instrumentationScope.schemaUrl).ifPresent(tracerBuilder::setSchemaUrl);
            reconfigurableTracer.setDelegate(tracerBuilder.build());
        });
    }

    @Override
    public Tracer get(String instrumentationScopeName, String instrumentationScopeVersion) {
        return tracers.computeIfAbsent(
            new InstrumentationScope(instrumentationScopeName, null, instrumentationScopeVersion),
            instrumentationScope -> new ReconfigurableTracer(delegate.get(instrumentationScopeName, instrumentationScopeVersion)));
    }

    @Override
    public TracerBuilder tracerBuilder(String instrumentationScopeName) {
        return new ReconfigurableTracerBuilder(delegate.tracerBuilder(instrumentationScopeName), instrumentationScopeName);
    }

    public synchronized TracerProvider getDelegate() {
        return delegate;
    }

    @VisibleForTesting
    protected class ReconfigurableTracerBuilder implements TracerBuilder {
        TracerBuilder delegate;
        String instrumentationScopeName;
        String schemaUrl;
        String instrumentationScopeVersion;

        public ReconfigurableTracerBuilder(TracerBuilder delegate, String instrumentationScopeName) {
            this.delegate = Objects.requireNonNull(delegate);
            this.instrumentationScopeName = Objects.requireNonNull(instrumentationScopeName);
        }

        @Override
        public TracerBuilder setSchemaUrl(String schemaUrl) {
            delegate.setSchemaUrl(schemaUrl);
            this.schemaUrl = schemaUrl;
            return this;
        }

        @Override
        public TracerBuilder setInstrumentationVersion(String instrumentationScopeVersion) {
            delegate.setInstrumentationVersion(instrumentationScopeVersion);
            this.instrumentationScopeVersion = instrumentationScopeVersion;
            return this;
        }

        @Override
        public Tracer build() {
            InstrumentationScope instrumentationScope = new InstrumentationScope(instrumentationScopeName, schemaUrl, instrumentationScopeVersion);
            return tracers.computeIfAbsent(instrumentationScope, k -> new ReconfigurableTracer(delegate.build()));
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableTracer implements Tracer {
        Tracer delegate;

        public ReconfigurableTracer(Tracer delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized SpanBuilder spanBuilder(@Nonnull String spanName) {
            return delegate.spanBuilder(spanName);
        }

        public synchronized void setDelegate(Tracer delegate) {
            this.delegate = delegate;
        }

        public synchronized Tracer getDelegate() {
            return delegate;
        }
    }

}
