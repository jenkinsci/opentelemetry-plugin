/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.lang.ref.WeakReference;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.MemoryAssert;

class OpenTelemetryAttributesActionTest {

    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
    private final Tracer tracer = tracerProvider.get("test");

    @AfterEach
    void closeTracerProvider() {
        tracerProvider.close();
    }

    @Test
    void spanIsRememberedOnce() {
        OpenTelemetryAttributesAction action = new OpenTelemetryAttributesAction();
        Span span = tracer.spanBuilder("span-1").startSpan();
        assertTrue(action.isNotYetAppliedToSpan(span));
        assertFalse(action.isNotYetAppliedToSpan(span));
    }

    @Test
    void openSpanIsRememberedWhileOtherSpansComeAndGo() {
        OpenTelemetryAttributesAction action = new OpenTelemetryAttributesAction();
        Span openSpan = tracer.spanBuilder("long-running-stage").startSpan();
        assertTrue(action.isNotYetAppliedToSpan(openSpan));
        for (int i = 0; i < 10_000; i++) {
            Span shortSpan = tracer.spanBuilder("step-" + i).startSpan();
            assertTrue(action.isNotYetAppliedToSpan(shortSpan));
            shortSpan.end();
        }
        System.gc();
        // the open span must not be seen as new, otherwise the attributes are applied again over overrides
        assertFalse(action.isNotYetAppliedToSpan(openSpan));
        openSpan.end();
    }

    @Test
    void endedSpanIsNotRetained() {
        OpenTelemetryAttributesAction action = new OpenTelemetryAttributesAction();
        Span span = tracer.spanBuilder("span-1").startSpan();
        assertTrue(action.isNotYetAppliedToSpan(span));
        span.end();
        WeakReference<Span> ref = new WeakReference<>(span);
        span = null;
        MemoryAssert.assertGC(ref, false);
    }

    @Test
    void replaceAttributesDoesNotChangeTheMapAReaderHolds() {
        AttributeKey<String> key = AttributeKey.stringKey("host.name");
        OpenTelemetryAttributesAction action = new OpenTelemetryAttributesAction();
        action.getAttributes().put(key, "old");
        Map<AttributeKey<?>, Object> heldByReader = action.getAttributes();

        action.replaceAttributes(Map.of(key, "new"));

        assertEquals(Map.of(key, "old"), heldByReader);
        assertEquals(Map.of(key, "new"), action.getAttributes());
    }
}
