/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.action;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Action;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractMonitoringAction implements Action, OtelMonitoringAction {
    private final static Logger LOGGER = Logger.getLogger(AbstractMonitoringAction.class.getName());

    private transient Span span;
    final String traceId;
    final String spanId;
    protected String spanName;
    protected Map<String, String> w3cTraceContext;

    public AbstractMonitoringAction(Span span) {
        this.span = span;
        this.traceId = span.getSpanContext().getTraceId();
        this.spanId = span.getSpanContext().getSpanId();
        this.spanName = span instanceof ReadWriteSpan ? ((ReadWriteSpan) span).getName() : null; // when tracer is no-op, span is NOT a ReadWriteSpan
        try (Scope scope = span.makeCurrent()) {
            Map<String, String> w3cTraceContext = new HashMap<>();
            W3CTraceContextPropagator.getInstance().inject(Context.current(), w3cTraceContext, (carrier, key, value) -> carrier.put(key, value));
            this.w3cTraceContext = w3cTraceContext;
        }
    }

    public String getSpanName() {
        return spanName;
    }

    @Override
    public Map<String, String> getW3cTraceContext() {
        return Collections.unmodifiableMap(w3cTraceContext);
    }

    @Override
    @CheckForNull
    public Span getSpan() {
        return span;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    @Override
    public void purgeSpan() {
        LOGGER.log(Level.FINE, () -> "Purge span='" + spanName + "', spanId=" + spanId + ", traceId=" + traceId + ": " + (span == null ? "#null#" : "purged"));
        this.span = null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
            "traceId='" + traceId + '\'' +
            ", spanId='" + spanId + '\'' +
            ", span.name='" + spanName + '\'' +
            '}';
    }

    @Override
    public boolean hasEnded() {
        return
            Optional
                .ofNullable(span).map(s -> s instanceof ReadableSpan ? (ReadableSpan) s : null) // cast to ReadableSpan
                .map(ReadableSpan::hasEnded)
                .orElse(true);
    }
}
