/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.testing.trace;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.mockito.Mockito;

/**
 * Mock class for {@link Span}. It exposes the attributes for unit testing.
 */
public class SpanMock implements Span {

    private final String spanName;
    private final Span delegate;
    private final Map<AttributeKey<?>, Object> attributesMap = new HashMap<>();

    public SpanMock(String spanName) {
        this.spanName = spanName;
        this.delegate = new SpanBuilderMock(spanName).startSpan();
    }

    public Map<AttributeKey<?>, Object> getAttributes() {
        return attributesMap;
    }

    @Override
    public Span setAttribute(String key, String value) {
        attributesMap.put(AttributeKey.stringKey(key), value);
        return delegate.setAttribute(key, value);
    }

    @Override
    public Span setAttribute(String key, long value) {
        attributesMap.put(AttributeKey.stringKey(key), value);
        return delegate.setAttribute(key, value);
    }

    @Override
    public Span setAttribute(String key, double value) {
        attributesMap.put(AttributeKey.stringKey(key), value);
        return delegate.setAttribute(key, value);
    }

    @Override
    public Span setAttribute(String key, boolean value) {
        attributesMap.put(AttributeKey.stringKey(key), value);
        return delegate.setAttribute(key, value);
    }

    @Override
    public Span setAttribute(AttributeKey<Long> key, int value) {
        attributesMap.put(key, value);
        return delegate.setAttribute(key, value);
    }

    @Override
    public Span setAllAttributes(Attributes attributes) {
        if (!attributes.isEmpty()) {
            attributes.forEach(attributesMap::put);
        }
        return delegate.setAllAttributes(attributes);
    }

    @Override
    public Span addEvent(String name) {
        return delegate.addEvent(name);
    }

    @Override
    public Span addEvent(String name, long timestamp, TimeUnit unit) {
        return delegate.addEvent(name, timestamp, unit);
    }

    @Override
    public Span addEvent(String name, Instant timestamp) {
        return delegate.addEvent(name, timestamp);
    }

    @Override
    public Span addEvent(String name, Attributes attributes, Instant timestamp) {
        return delegate.addEvent(name, attributes, timestamp);
    }

    @Override
    public Span setStatus(StatusCode statusCode) {
        return delegate.setStatus(statusCode);
    }

    @Override
    public Span recordException(Throwable exception) {
        return delegate.recordException(exception);
    }

    @Override
    public Span addLink(SpanContext spanContext) {
        return delegate.addLink(spanContext);
    }

    @Override
    public Span addLink(SpanContext spanContext, Attributes attributes) {
        return delegate.addLink(spanContext, attributes);
    }

    @Override
    public void end(Instant timestamp) {
        delegate.end(timestamp);
    }

    @Override
    public Context storeInContext(Context context) {
        return delegate.storeInContext(context);
    }

    @Override
    public <T> Span setAttribute(AttributeKey<T> attributeKey, T t) {
        attributesMap.put(attributeKey, t);
        return delegate.setAttribute(attributeKey, t);
    }

    @Override
    public Span addEvent(String s, Attributes attributes) {
        return delegate.addEvent(s, attributes);
    }

    @Override
    public Span addEvent(String s, Attributes attributes, long l, TimeUnit timeUnit) {
        return delegate.addEvent(s, attributes, l, timeUnit);
    }

    @Override
    public Span setStatus(StatusCode statusCode, String s) {
        return delegate.setStatus(statusCode, s);
    }

    @Override
    public Span recordException(Throwable throwable, Attributes attributes) {
        return delegate.recordException(throwable, attributes);
    }

    @Override
    public Span updateName(String s) {
        return delegate.updateName(s);
    }

    @Override
    public void end() {
        delegate.end();
    }

    @Override
    public void end(long l, TimeUnit timeUnit) {
        delegate.end(l, timeUnit);
    }

    @Override
    public SpanContext getSpanContext() {
        // The span id is always 0000000000000000.
        // Spy the object and stub it to provide a new id.
        SpanContext spanContext = Mockito.spy(delegate.getSpanContext());
        Mockito.when(spanContext.getSpanId()).thenReturn("spanId-" + spanName);
        return spanContext;
    }

    @Override
    public boolean isRecording() {
        return delegate.isRecording();
    }
}
