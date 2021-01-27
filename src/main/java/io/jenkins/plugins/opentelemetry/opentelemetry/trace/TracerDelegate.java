package io.jenkins.plugins.opentelemetry.opentelemetry.trace;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;

public class TracerDelegate implements Tracer {
    private Tracer delegate;

    public TracerDelegate(Tracer delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized SpanBuilder spanBuilder(String spanName) {
        return delegate.spanBuilder(spanName);
    }

    public synchronized void setDelegate(Tracer delegate) {
        this.delegate = delegate;
    }
}
