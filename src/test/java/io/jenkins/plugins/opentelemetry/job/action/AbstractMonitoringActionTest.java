/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.action;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AbstractMonitoringActionTest {

    final SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
    final Tracer tracer = tracerProvider.get("test");

    @AfterEach
    void afterEach() {
        tracerProvider.close();
    }

    @Test
    void sdk_span_has_ended_once_ended() {
        Span span = tracer.spanBuilder("sdk-span").startSpan();
        RunPhaseMonitoringAction action = new RunPhaseMonitoringAction(span);

        assertFalse(action.hasEnded());
        span.end();
        assertTrue(action.hasEnded());
    }

    @Test
    void span_of_another_opentelemetry_implementation_has_ended_once_ended() {
        // The OpenTelemetry Java agent gives the application spans that wrap the agent's SDK spans and are not
        // io.opentelemetry.sdk.trace.ReadableSpan instances
        Span span = new WrappingSpan(tracer.spanBuilder("wrapped-span").startSpan());
        RunPhaseMonitoringAction action = new RunPhaseMonitoringAction(span);

        assertFalse(action.hasEnded());
        span.end();
        assertTrue(action.hasEnded());
    }

    @Test
    void non_recording_span_counts_as_ended() {
        try (SdkTracerProvider sampledOut =
                SdkTracerProvider.builder().setSampler(Sampler.alwaysOff()).build()) {
            Span notSampled = sampledOut.get("test").spanBuilder("not-sampled").startSpan();

            assertTrue(new RunPhaseMonitoringAction(Span.getInvalid()).hasEnded());
            assertTrue(new RunPhaseMonitoringAction(notSampled).hasEnded());
        }
    }

    @Test
    void purged_action_counts_as_ended() {
        RunPhaseMonitoringAction action =
                new RunPhaseMonitoringAction(tracer.spanBuilder("purged").startSpan());

        action.purgeSpanAndCloseAssociatedScopes();

        assertTrue(action.hasEnded());
    }

    /** A span that delegates to another span and is not an SDK span. */
    static class WrappingSpan implements Span {
        final Span delegate;

        WrappingSpan(Span delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> Span setAttribute(AttributeKey<T> key, T value) {
            delegate.setAttribute(key, value);
            return this;
        }

        @Override
        public Span addEvent(String name, Attributes attributes) {
            delegate.addEvent(name, attributes);
            return this;
        }

        @Override
        public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
            delegate.addEvent(name, attributes, timestamp, unit);
            return this;
        }

        @Override
        public Span setStatus(StatusCode statusCode, String description) {
            delegate.setStatus(statusCode, description);
            return this;
        }

        @Override
        public Span recordException(Throwable exception, Attributes additionalAttributes) {
            delegate.recordException(exception, additionalAttributes);
            return this;
        }

        @Override
        public Span updateName(String name) {
            delegate.updateName(name);
            return this;
        }

        @Override
        public void end() {
            delegate.end();
        }

        @Override
        public void end(long timestamp, TimeUnit unit) {
            delegate.end(timestamp, unit);
        }

        @Override
        public SpanContext getSpanContext() {
            return delegate.getSpanContext();
        }

        @Override
        public boolean isRecording() {
            return delegate.isRecording();
        }
    }
}
