/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;


import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerBuilder;
import io.opentelemetry.api.logs.LoggerProvider;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ReconfigurableLoggerProviderTest {

    @org.junit.Test
    public void testLoggerBuilder() {
        ReconfigurableLoggerProvider loggerProvider = new ReconfigurableLoggerProvider();

        ReconfigurableLoggerProviderTest.LoggerProviderMock loggerProviderImpl_1 = new ReconfigurableLoggerProviderTest.LoggerProviderMock();
        loggerProvider.setDelegate(loggerProviderImpl_1);

        ReconfigurableLoggerProvider.ReconfigurableLogger authenticationLogger = (ReconfigurableLoggerProvider.ReconfigurableLogger) loggerProvider
            .loggerBuilder("io.jenkins.authentication")
            .setInstrumentationVersion("1.0.0")
            .build();

        ReconfigurableLoggerProviderTest.LoggerMock authenticationLoggerImpl = (ReconfigurableLoggerProviderTest.LoggerMock) authenticationLogger.delegate;
        assertEquals("io.jenkins.authentication", authenticationLoggerImpl.instrumentationScopeName);
        assertNull(authenticationLoggerImpl.schemaUrl);
        assertEquals("1.0.0", authenticationLoggerImpl.instrumentationVersion);
        assertEquals(loggerProviderImpl_1.id, authenticationLoggerImpl.loggerProviderId);


        ReconfigurableLoggerProvider.ReconfigurableLogger buildLogger = (ReconfigurableLoggerProvider.ReconfigurableLogger) loggerProvider
            .loggerBuilder("io.jenkins.build")
            .setSchemaUrl("https://jenkins.io/build")
            .build();
        ReconfigurableLoggerProviderTest.LoggerMock buildLoggerImpl = (ReconfigurableLoggerProviderTest.LoggerMock) buildLogger.delegate;
        assertEquals("io.jenkins.build", buildLoggerImpl.instrumentationScopeName);
        assertEquals("https://jenkins.io/build", buildLoggerImpl.schemaUrl);
        assertNull(buildLoggerImpl.instrumentationVersion);
        assertEquals(loggerProviderImpl_1.id, buildLoggerImpl.loggerProviderId);

        ReconfigurableLoggerProvider.ReconfigurableLogger buildLoggerShouldBeTheSameInstance = (ReconfigurableLoggerProvider.ReconfigurableLogger) loggerProvider
            .loggerBuilder("io.jenkins.build")
            .setSchemaUrl("https://jenkins.io/build")
            .build();

        assertEquals(buildLogger, buildLoggerShouldBeTheSameInstance);

        ReconfigurableLoggerProviderTest.LoggerProviderMock loggerProviderImpl_2 = new ReconfigurableLoggerProviderTest.LoggerProviderMock();
        assertNotEquals(loggerProviderImpl_1.id, loggerProviderImpl_2.id);

        // CHANGE THE IMPLEMENTATION OF THE EVENT LOGGER PROVIDER
        loggerProvider.setDelegate(loggerProviderImpl_2);

        // VERIFY THE DELEGATE IMPL HAS CHANGED WHILE THE PARAMS REMAINS UNCHANGED
        ReconfigurableLoggerProviderTest.LoggerMock authenticationLoggerImpl_2 = (ReconfigurableLoggerProviderTest.LoggerMock) authenticationLogger.delegate;
        assertEquals("io.jenkins.authentication", authenticationLoggerImpl_2.instrumentationScopeName);
        assertNull(authenticationLoggerImpl_2.schemaUrl);
        assertEquals("1.0.0", authenticationLoggerImpl_2.instrumentationVersion);
        assertEquals(loggerProviderImpl_2.id, authenticationLoggerImpl_2.loggerProviderId);

        ReconfigurableLoggerProviderTest.LoggerMock buildLoggerImpl_2 = (ReconfigurableLoggerProviderTest.LoggerMock) buildLogger.delegate;

        assertEquals("io.jenkins.build", buildLoggerImpl_2.instrumentationScopeName);
        assertEquals("https://jenkins.io/build", buildLoggerImpl_2.schemaUrl);
        assertNull(buildLoggerImpl_2.instrumentationVersion);
        assertEquals(loggerProviderImpl_2.id, buildLoggerImpl_2.loggerProviderId);
    }


    static class LoggerProviderMock implements LoggerProvider {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String id;

        public LoggerProviderMock() {
            this.id = "LoggerProviderMock-" + ID_SOURCE.incrementAndGet();
        }

        @Override
        public LoggerBuilder loggerBuilder(String instrumentationScopeName) {
            return new ReconfigurableLoggerProviderTest.LoggerBuilderMock(instrumentationScopeName, id);
        }

        @Override
        public Logger get(String instrumentationScopeName) {
            return new ReconfigurableLoggerProviderTest.LoggerMock(instrumentationScopeName, id);
        }
    }

    static class LoggerMock implements Logger {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);

        final String instrumentationScopeName;
        final String loggerProviderId;
        final String id;

        final String schemaUrl;
        final String instrumentationVersion;

        public LoggerMock(String instrumentationScopeName, String loggerProviderId) {
            this(instrumentationScopeName, loggerProviderId, null, null);
        }
        public LoggerMock(String instrumentationScopeName, String loggerProviderId, @Nullable String schemaUrl, @Nullable String instrumentationVersion) {
            this.id = "LoggerMock-" + ID_SOURCE.incrementAndGet();
            this.instrumentationScopeName = instrumentationScopeName;
            this.loggerProviderId = loggerProviderId;
            this.schemaUrl = schemaUrl;
            this.instrumentationVersion = instrumentationVersion;
        }

        @Override
        public LogRecordBuilder logRecordBuilder() {
            throw new UnsupportedOperationException();
        }
    }

    static class LoggerBuilderMock implements LoggerBuilder {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String id;
        final String instrumentationScopeName;
        final String loggerProviderId;
        String schemaUrl;
        String instrumentationVersion;


        public LoggerBuilderMock(String instrumentationScopeName, String loggerProviderId) {
            this.id = "LoggerBuilderMock-" + ID_SOURCE.incrementAndGet();
            this.instrumentationScopeName = instrumentationScopeName;
            this.loggerProviderId = loggerProviderId;
        }

        @Override
        public LoggerBuilder setSchemaUrl(String schemaUrl) {
            this.schemaUrl = schemaUrl;
            return this;
        }

        @Override
        public LoggerBuilder setInstrumentationVersion(String instrumentationVersion) {
            this.instrumentationVersion = instrumentationVersion;
            return this;
        }

        @Override
        public Logger build() {
            return new ReconfigurableLoggerProviderTest.LoggerMock(instrumentationScopeName, loggerProviderId, schemaUrl, instrumentationVersion);
        }
    }
}