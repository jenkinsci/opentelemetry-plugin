/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.action;

import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractMonitoringAction implements Action, OtelMonitoringAction {
    private final static Logger LOGGER = Logger.getLogger(AbstractMonitoringAction.class.getName());

    @CheckForNull
    transient SpanAndScopes spanAndScopes;

    final String traceId;
    final String spanId;
    protected String spanName;
    protected Map<String, String> w3cTraceContext;

    /**
     * @param span   span of this action
     * @param scopes scope and underlying scopes associated with the span.
     */
    public AbstractMonitoringAction(Span span, List<Scope> scopes) {
        this.spanAndScopes = new SpanAndScopes(span, scopes, Thread.currentThread().getName());
        this.traceId = span.getSpanContext().getTraceId();
        this.spanId = span.getSpanContext().getSpanId();
        this.spanName = span instanceof ReadWriteSpan ? ((ReadWriteSpan) span).getName() : null; // when tracer is no-op, span is NOT a ReadWriteSpan
        try (Scope scope = span.makeCurrent()) {
            Map<String, String> w3cTraceContext = new HashMap<>();
            W3CTraceContextPropagator.getInstance().inject(Context.current(), w3cTraceContext, (carrier, key, value) -> carrier.put(key, value));
            this.w3cTraceContext = w3cTraceContext;
        }

        LOGGER.log(Level.FINE, () -> "Span " + getSpanName() + Optional.ofNullable(spanAndScopes).map(sas -> ", thread=" + sas.scopeStartThreadName + " opened " + sas.scopes.size() + " scopes").orElse(", null spanAndScopes") );
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
        return spanAndScopes == null ? null : spanAndScopes.span;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    @Override
    public void purgeSpanAndCloseAssociatedScopes() {
        LOGGER.log(Level.FINE, () -> "Purge span='" + spanName + "', spanId=" + spanId + ", traceId=" + traceId + ": " + spanAndScopes);
        Optional.ofNullable(spanAndScopes)
            .map(spanAndScopes -> spanAndScopes.scopes)
            .map(ImmutableList::copyOf)
            .map(ImmutableList::reverse)
            .ifPresent(scopes -> scopes.forEach(Scope::close));
        this.spanAndScopes = null;
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
                .ofNullable(spanAndScopes)
                .map(sac -> sac.span)
                .filter(s -> s instanceof ReadableSpan)
                .map(s -> (ReadableSpan) s)
                .map(ReadableSpan::hasEnded)
                .orElse(true);
    }

    /**
     * Scopes associated with the span and the underlying scopes instantiated to create the span.
     * Underlying scopes can be the scope of the underlying wrapping pipeline step (eg a `stage` step).
     * Thread name when the scope was opened. Used for debugging, to identify potential leaks.
     */
    static class SpanAndScopes {
        @NonNull
        final Span span;
        @NonNull
        final List<Scope> scopes;
        @NonNull
        final String scopeStartThreadName;

        public SpanAndScopes(@NonNull Span span, @NonNull List<Scope> scopes, @NonNull String scopeStartThreadName) {
            this.span = span;
            this.scopes = scopes;
            this.scopeStartThreadName = scopeStartThreadName;
        }

        @Override
        public String toString() {
            return "SpanAndScopes{" +
                "span=" + span +
                ", scopes=" + scopes.size() +
                ", scopeStartThreadName='" + scopeStartThreadName + '\'' +
                '}';
        }
    }
}
