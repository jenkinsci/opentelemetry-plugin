/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

/**
 * Regression tests for <a href="https://github.com/jenkinsci/opentelemetry-plugin/issues/1291">issue #1291</a>:
 * {@link OtelJulHandler} used to permanently disable log export after a single emit failure, with no code
 * path that ever reset the circuit breaker.
 */
class OtelJulHandlerTest {

    @Test
    void handlerNeverRecoversWithoutAfterConfiguration() throws Exception {
        AtomicInteger emitAttempts = new AtomicInteger();
        OtelJulHandler handler = newHandler(emitAttempts);

        // 1st record: emit() throws, the circuit breaker latches disabled = true.
        handler.publish(new LogRecord(Level.INFO, "first record - endpoint transiently down"));
        // 2nd record: the endpoint has recovered, but the handler short-circuits on `disabled`.
        handler.publish(new LogRecord(Level.INFO, "second record - endpoint back up"));

        assertEquals(
                1,
                emitAttempts.get(),
                "without a reset path, emit() is attempted only once and every subsequent log is dropped");
        assertTrue(getDisabled(handler), "disabled must latch true after the first emit failure");
    }

    @Test
    void afterConfigurationResetsTheCircuitBreaker() throws Exception {
        AtomicInteger emitAttempts = new AtomicInteger();
        OtelJulHandler handler = newHandler(emitAttempts);

        handler.publish(new LogRecord(Level.INFO, "first record - endpoint transiently down"));
        assertTrue(getDisabled(handler), "disabled must latch true after the first emit failure");

        handler.afterConfiguration(DefaultConfigProperties.createFromMap(Collections.emptyMap()));
        assertTrue(!getDisabled(handler), "afterConfiguration() must clear the circuit breaker");

        handler.publish(new LogRecord(Level.INFO, "second record - after reconfiguration"));
        assertEquals(
                2,
                emitAttempts.get(),
                "after afterConfiguration() resets the breaker, publish() must attempt emit() again");
    }

    private static OtelJulHandler newHandler(AtomicInteger emitAttempts) throws Exception {
        LogRecordBuilder builder = mock(LogRecordBuilder.class, Answers.RETURNS_SELF);
        doAnswer(invocation -> {
                    if (emitAttempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("simulated transient OTLP emit failure");
                    }
                    return null;
                })
                .when(builder)
                .emit();

        Logger otelLogger = mock(Logger.class);
        when(otelLogger.logRecordBuilder()).thenReturn(builder);
        LoggerProvider provider = mock(LoggerProvider.class);
        when(provider.get(anyString())).thenReturn(otelLogger);

        OtelJulHandler handler = new OtelJulHandler();
        setField(handler, "loggerProvider", provider);

        ReconfigurableOpenTelemetry openTelemetry = mock(ReconfigurableOpenTelemetry.class);
        when(openTelemetry.getLogsBridge()).thenReturn(provider);
        setField(handler, "openTelemetry", openTelemetry);

        return handler;
    }

    private static void setField(OtelJulHandler handler, String fieldName, Object value) throws Exception {
        Field field = OtelJulHandler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(handler, value);
    }

    private static boolean getDisabled(OtelJulHandler handler) throws Exception {
        Field field = OtelJulHandler.class.getDeclaredField("disabled");
        field.setAccessible(true);
        return (boolean) field.get(handler);
    }
}
