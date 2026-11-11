/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    @Test
    void replacementIsAnImmutableDefensiveCopy() {
        AttributeKey<String> key = AttributeKey.stringKey("host.name");
        OpenTelemetryAttributesAction action = new OpenTelemetryAttributesAction();
        Map<AttributeKey<?>, Object> replacement = new HashMap<>(Map.of(key, "new"));

        action.replaceAttributes(replacement);
        replacement.clear();

        assertEquals(Map.of(key, "new"), action.getAttributes());
        assertThrows(
                UnsupportedOperationException.class,
                () -> action.getAttributes().clear());
    }

    @Test
    void concurrentReadersSeeCompleteSnapshots() throws Exception {
        AttributeKey<String> hostName = AttributeKey.stringKey("host.name");
        AttributeKey<String> hostIp = AttributeKey.stringKey("host.ip");
        Map<AttributeKey<?>, Object> oldAttributes = Map.of(hostName, "old-host", hostIp, "192.0.2.1");
        Map<AttributeKey<?>, Object> newAttributes = Map.of(hostName, "new-host", hostIp, "192.0.2.2");
        OpenTelemetryAttributesAction action = new OpenTelemetryAttributesAction();
        action.replaceAttributes(oldAttributes);
        CountDownLatch start = new CountDownLatch(1);

        var executor = Executors.newSingleThreadExecutor();
        try {
            var writer = executor.submit(() -> {
                start.await();
                for (int i = 0; i < 10_000; i++) {
                    action.replaceAttributes(i % 2 == 0 ? newAttributes : oldAttributes);
                }
                return null;
            });
            start.countDown();
            for (int i = 0; i < 10_000; i++) {
                Map<AttributeKey<?>, Object> snapshot = new HashMap<>(action.getAttributes());
                assertTrue(snapshot.equals(oldAttributes) || snapshot.equals(newAttributes));
            }
            writer.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
