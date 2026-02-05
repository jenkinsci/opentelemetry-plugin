/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerProvider;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.Before;
import org.junit.Test;

public class OtelJulHandlerTest {

    private OtelJulHandler handler;
    private LogRecordBuilder mockLogRecordBuilder;

    @Before
    public void setUp() throws Exception {
        handler = new OtelJulHandler();

        LoggerProvider mockProvider = mock(LoggerProvider.class);
        Logger mockLogger = mock(Logger.class);
        mockLogRecordBuilder = mock(LogRecordBuilder.class);
        ReconfigurableOpenTelemetry mockOpenTelemetry = mock(ReconfigurableOpenTelemetry.class);

        when(mockProvider.get(anyString())).thenReturn(mockLogger);
        when(mockLogger.logRecordBuilder()).thenReturn(mockLogRecordBuilder);
        when(mockLogRecordBuilder.setBody(anyString())).thenReturn(mockLogRecordBuilder);
        when(mockLogRecordBuilder.setTimestamp(any())).thenReturn(mockLogRecordBuilder);
        when(mockLogRecordBuilder.setSeverity(any())).thenReturn(mockLogRecordBuilder);
        when(mockLogRecordBuilder.setSeverityText(anyString())).thenReturn(mockLogRecordBuilder);
        when(mockLogRecordBuilder.setAllAttributes(any())).thenReturn(mockLogRecordBuilder);
        when(mockLogRecordBuilder.setContext(any())).thenReturn(mockLogRecordBuilder);

        setField(handler, "openTelemetry", mockOpenTelemetry);
        setField(handler, "loggerProvider", mockProvider);
    }

    @Test
    public void publish_catchesThrowable_andDisablesHandler() throws Exception {
        doThrow(new LinkageError("boom")).when(mockLogRecordBuilder).emit();

        handler.publish(new LogRecord(Level.INFO, "test"));
        assertTrue("Handler should be disabled after crash", getDisabled(handler));
    }

    @Test
    public void publish_preventsRecursiveLogging() throws Exception {
        doAnswer(invocation -> {
                    handler.publish(new LogRecord(Level.INFO, "recursive"));
                    return null;
                })
                .when(mockLogRecordBuilder)
                .emit();

        handler.publish(new LogRecord(Level.INFO, "outer"));
        verify(mockLogRecordBuilder, times(1)).emit();
        assertFalse("Handler disabled itself, which means recursion caused an error", getDisabled(handler));
    }

    @Test
    public void publish_noop_whenLoggerProviderNull() throws Exception {
        setField(handler, "loggerProvider", null);
        handler.publish(new LogRecord(Level.INFO, "test"));
        verifyNoInteractions(mockLogRecordBuilder);
    }

    @Test
    public void publish_noop_whenDisabled() throws Exception {
        setField(handler, "disabled", true);
        handler.publish(new LogRecord(Level.INFO, "test"));
        verifyNoInteractions(mockLogRecordBuilder);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static boolean getDisabled(OtelJulHandler handler) throws Exception {
        Field f = handler.getClass().getDeclaredField("disabled");
        f.setAccessible(true);
        return (boolean) f.get(handler);
    }
}
