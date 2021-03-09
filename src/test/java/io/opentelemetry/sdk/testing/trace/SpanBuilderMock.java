/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.testing.trace;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SpanBuilderMock implements SpanBuilder {

    private SpanBuilder delegate;

    private Map<AttributeKey, Object> attributes = new HashMap<>();

    public SpanBuilderMock(String spanName) {
        this.delegate = Tracer.getDefault().spanBuilder(spanName);
    }

    @Override
    public SpanBuilder setParent(Context context) {
        delegate.setParent(context);
        return this;
    }

    @Override
    public SpanBuilder setNoParent() {
        delegate.setNoParent();
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext) {
        delegate.addLink(spanContext);
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext, Attributes attributes) {
        delegate.addLink(spanContext, attributes);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, @Nonnull String value) {
        this.attributes.put(AttributeKey.stringKey(key), value);
        delegate.setAttribute(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, long value) {
        this.attributes.put(AttributeKey.longKey(key), value);
        delegate.setAttribute(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, double value) {
        this.attributes.put(AttributeKey.doubleKey(key), value);
        delegate.setAttribute(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, boolean value) {
        this.attributes.put(AttributeKey.booleanKey(key), value);
        delegate.setAttribute(key, value);
        return this;
    }

    @Override
    public <T> SpanBuilder setAttribute(AttributeKey<T> key, @Nonnull T value) {
        this.attributes.put(key, value);
        delegate.setAttribute(key, value);
        return this;
    }

    @Override
    public SpanBuilder setSpanKind(SpanKind spanKind) {
        this.delegate.setSpanKind(spanKind);
        return this;
    }

    @Override
    public SpanBuilder setStartTimestamp(long startTimestamp, TimeUnit unit) {
        delegate.setStartTimestamp(startTimestamp, unit);
        return this;
    }

    @Override
    public SpanBuilder setStartTimestamp(Instant startTimestamp) {
        delegate.setStartTimestamp(startTimestamp);
        return this;
    }

    @Override
    public Span startSpan() {
        return delegate.startSpan();
    }

    @Nonnull
    public Map<AttributeKey, Object> getAttributes() {
        return attributes;
    }
}
