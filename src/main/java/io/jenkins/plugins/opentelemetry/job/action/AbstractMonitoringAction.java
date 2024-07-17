/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.action;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
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

    transient SpanAndScope spanAndScope;


    final String traceId;
    final String spanId;
    protected String spanName;
    protected Map<String, String> w3cTraceContext;

    /**
     * @param span   span of this action
     * @param scope scope and underlying scopes associated with the span.
     */
    public AbstractMonitoringAction(@NonNull Span span, @Nullable Scope scope) {
        this.spanAndScope = new SpanAndScope(span, scope, scope == null ? null : Thread.currentThread().getName());
        this.traceId = span.getSpanContext().getTraceId();
        this.spanId = span.getSpanContext().getSpanId();
        this.spanName = span instanceof ReadWriteSpan ? ((ReadWriteSpan) span).getName() : null; // when tracer is no-op, span is NOT a ReadWriteSpan
        Map<String, String> w3cTraceContext = new HashMap<>();
        W3CTraceContextPropagator.getInstance().inject(Context.current().with(span), w3cTraceContext, (carrier, key, value) -> carrier.put(key, value));
        this.w3cTraceContext = w3cTraceContext;

        LOGGER.log(Level.FINE, () -> "Span " + getSpanName() + ", thread=" + spanAndScope.scopeStartThreadName + " scope " + spanAndScope.scope);
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
        return spanAndScope.span;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    @Override
    public void purgeSpanAndCloseAssociatedScopes() {
        LOGGER.log(Level.FINE, () -> "Purge span='" + spanName + "', spanId=" + spanId + ", traceId=" + traceId + ": " + spanAndScope);
        Optional.ofNullable(spanAndScope)
            .map(SpanAndScope::getScope)
            .ifPresent(Scope::close);
        this.spanAndScope = null;
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
                .ofNullable(spanAndScope)
                .map(sac -> sac.span)
                .filter(s -> s instanceof ReadableSpan)
                .map(s -> (ReadableSpan) s)
                .map(ReadableSpan::hasEnded)
                .orElse(true);
    }

    /**
     * Span and associated scope
     */
    static class SpanAndScope {
        @NonNull
        final Span span;
        @Nullable
        final Scope scope;
        @Nullable
        final String scopeStartThreadName;

        public SpanAndScope(@NonNull Span span, @Nullable Scope scope, @Nullable String scopeStartThreadName) {
            this.span = span;
            this.scope = scope;
            this.scopeStartThreadName = scopeStartThreadName;
        }

        @NonNull
        public Span getSpan() {
            return span;
        }

        @Nullable
        public Scope getScope() {
            return scope;
        }

        @Nullable
        public String getScopeStartThreadName() {
            return scopeStartThreadName;
        }

        @Override
        public String toString() {
            return "SpanAndScope{" +
                "span=" + span +
                ", scope=" + scope +
                ", scopeStartThreadName='" + scopeStartThreadName + '\'' +
                '}';
        }
    }
}
