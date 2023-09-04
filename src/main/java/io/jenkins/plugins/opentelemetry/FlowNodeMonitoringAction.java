/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.InvisibleAction;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.ReadWriteSpan;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FlowNodeMonitoringAction extends InvisibleAction {
    private final static Logger LOGGER = Logger.getLogger(FlowNodeMonitoringAction.class.getName());

    private transient Span span;
    final String traceId;
    final String spanId;
    final String spanName;

    final Map<String, String> w3cTraceContext;

    public FlowNodeMonitoringAction(Span span) {
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

    @CheckForNull
    public Span getSpan() {
        return span;
    }

    public void purgeSpan() {
        LOGGER.log(Level.INFO, () -> "Purge span='" + spanName + "', spanId=" + spanId + ", traceId=" + traceId + ": " + (span == null ? "#null#" : "purged"));
        this.span = null;
    }

    @Override
    public String toString() {
        return "FlowNodeMonitoringAction{" +
            "traceId='" + traceId + '\'' +
            ", spanId='" + spanId + '\'' +
            ", span.name='" + spanName + '\'' +
            '}';
    }
}
