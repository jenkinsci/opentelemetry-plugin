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
        OtelJulHandler handler = new OtelJulHandler();
        setField(handler, "loggerProvider", newFailFirstProvider(emitAttempts));

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
    void afterConfigurationResetsTheBreakerAndUsesTheRefreshedProvider() throws Exception {
        AtomicInteger oldProviderEmitAttempts = new AtomicInteger();
        LoggerProvider oldProvider = newFailFirstProvider(oldProviderEmitAttempts);
        AtomicInteger newProviderEmitAttempts = new AtomicInteger();
        LoggerProvider newProvider = newSucceedingProvider(newProviderEmitAttempts);

        OtelJulHandler handler = new OtelJulHandler();
        setField(handler, "loggerProvider", oldProvider);
        ReconfigurableOpenTelemetry openTelemetry = mock(ReconfigurableOpenTelemetry.class);
        setField(handler, "openTelemetry", openTelemetry);

        handler.publish(new LogRecord(Level.INFO, "first record - endpoint transiently down"));
        assertTrue(getDisabled(handler), "disabled must latch true after the first emit failure");
        assertEquals(1, oldProviderEmitAttempts.get());

        // afterConfiguration() re-fetches the provider from openTelemetry.getLogsBridge(); simulate the SDK
        // handing back a freshly (re)configured provider distinct from the one installed at construction time,
        // so the test can tell whether afterConfiguration() actually replaced loggerProvider rather than just
        // clearing the disabled flag.
        when(openTelemetry.getLogsBridge()).thenReturn(newProvider);
        handler.afterConfiguration(DefaultConfigProperties.createFromMap(Collections.emptyMap()));
        assertTrue(!getDisabled(handler), "afterConfiguration() must clear the circuit breaker");

        handler.publish(new LogRecord(Level.INFO, "second record - after reconfiguration"));

        assertEquals(
                1, newProviderEmitAttempts.get(), "publish() after reconfiguration must use the refreshed provider");
        assertEquals(1, oldProviderEmitAttempts.get(), "the superseded provider must not be used again after a reset");
    }

    @Test
    void staleInFlightFailureDoesNotReLatchTheBreakerAfterReconfiguration() throws Exception {
        OtelJulHandler handler = new OtelJulHandler();
        LoggerProvider replacementProvider = newSucceedingProvider(new AtomicInteger());
        ReconfigurableOpenTelemetry openTelemetry = mock(ReconfigurableOpenTelemetry.class);
        when(openTelemetry.getLogsBridge()).thenReturn(replacementProvider);
        setField(handler, "openTelemetry", openTelemetry);

        AtomicInteger emitAttempts = new AtomicInteger();
        LogRecordBuilder builder = mock(LogRecordBuilder.class, Answers.RETURNS_SELF);
        doAnswer(invocation -> {
                    emitAttempts.incrementAndGet();
                    // Simulate a reconfiguration racing in and completing while this emit() call, already in
                    // flight against the about-to-be-superseded provider below, is still running.
                    handler.afterConfiguration(DefaultConfigProperties.createFromMap(Collections.emptyMap()));
                    throw new IllegalStateException("stale provider failed after being superseded");
                })
                .when(builder)
                .emit();
        Logger otelLogger = mock(Logger.class);
        when(otelLogger.logRecordBuilder()).thenReturn(builder);
        LoggerProvider staleProvider = mock(LoggerProvider.class);
        when(staleProvider.get(anyString())).thenReturn(otelLogger);
        setField(handler, "loggerProvider", staleProvider);

        handler.publish(new LogRecord(Level.INFO, "in-flight against the stale provider"));

        assertEquals(1, emitAttempts.get());
        assertTrue(
                !getDisabled(handler),
                "a failure from a publish that was already in flight before a reconfiguration completed must not"
                        + " re-latch the breaker on behalf of a provider that has since been superseded");
    }

    private static LoggerProvider newFailFirstProvider(AtomicInteger emitAttempts) {
        LogRecordBuilder builder = mock(LogRecordBuilder.class, Answers.RETURNS_SELF);
        doAnswer(invocation -> {
                    if (emitAttempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("simulated transient OTLP emit failure");
                    }
                    return null;
                })
                .when(builder)
                .emit();
        return providerFor(builder);
    }

    private static LoggerProvider newSucceedingProvider(AtomicInteger emitAttempts) {
        LogRecordBuilder builder = mock(LogRecordBuilder.class, Answers.RETURNS_SELF);
        doAnswer(invocation -> {
                    emitAttempts.incrementAndGet();
                    return null;
                })
                .when(builder)
                .emit();
        return providerFor(builder);
    }

    private static LoggerProvider providerFor(LogRecordBuilder builder) {
        Logger otelLogger = mock(Logger.class);
        when(otelLogger.logRecordBuilder()).thenReturn(builder);
        LoggerProvider provider = mock(LoggerProvider.class);
        when(provider.get(anyString())).thenReturn(otelLogger);
        return provider;
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
