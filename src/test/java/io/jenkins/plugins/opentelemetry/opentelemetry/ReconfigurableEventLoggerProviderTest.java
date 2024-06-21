/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import io.opentelemetry.api.incubator.events.EventBuilder;
import io.opentelemetry.api.incubator.events.EventLogger;
import io.opentelemetry.api.incubator.events.EventLoggerBuilder;
import io.opentelemetry.api.incubator.events.EventLoggerProvider;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ReconfigurableEventLoggerProviderTest {

    @org.junit.Test
    public void testEventLoggerBuilder() {
        ReconfigurableEventLoggerProvider eventLoggerProvider = new ReconfigurableEventLoggerProvider();

        EventLoggerProviderMock eventLoggerProviderImpl_1 = new EventLoggerProviderMock();
        eventLoggerProvider.setDelegate(eventLoggerProviderImpl_1);

        ReconfigurableEventLoggerProvider.ReconfigurableEventLogger authenticationEventLogger = (ReconfigurableEventLoggerProvider.ReconfigurableEventLogger) eventLoggerProvider
            .eventLoggerBuilder("io.jenkins.authentication")
            .setInstrumentationVersion("1.0.0")
            .build();

        EventLoggerMock authenticationEventLoggerImpl = (EventLoggerMock) authenticationEventLogger.delegateEventLogger;
        assertEquals("io.jenkins.authentication", authenticationEventLoggerImpl.instrumentationScopeName);
        assertNull(authenticationEventLoggerImpl.schemaUrl);
        assertEquals("1.0.0", authenticationEventLoggerImpl.instrumentationVersion);
        assertEquals(eventLoggerProviderImpl_1.id, authenticationEventLoggerImpl.eventLoggerProviderId);


        ReconfigurableEventLoggerProvider.ReconfigurableEventLogger buildEventLogger = (ReconfigurableEventLoggerProvider.ReconfigurableEventLogger) eventLoggerProvider
            .eventLoggerBuilder("io.jenkins.build")
            .setSchemaUrl("https://jenkins.io/build")
            .build();
        EventLoggerMock buildEventLoggerImpl = (EventLoggerMock) buildEventLogger.delegateEventLogger;
        assertEquals("io.jenkins.build", buildEventLoggerImpl.instrumentationScopeName);
        assertEquals("https://jenkins.io/build", buildEventLoggerImpl.schemaUrl);
        assertNull(buildEventLoggerImpl.instrumentationVersion);
        assertEquals(eventLoggerProviderImpl_1.id, buildEventLoggerImpl.eventLoggerProviderId);

        ReconfigurableEventLoggerProvider.ReconfigurableEventLogger buildEventLoggerShouldBeTheSameInstance = (ReconfigurableEventLoggerProvider.ReconfigurableEventLogger) eventLoggerProvider
            .eventLoggerBuilder("io.jenkins.build")
            .setSchemaUrl("https://jenkins.io/build")
            .build();

        assertEquals(buildEventLogger, buildEventLoggerShouldBeTheSameInstance);

        EventLoggerProviderMock eventLoggerProviderImpl_2 = new EventLoggerProviderMock();
        assertNotEquals(eventLoggerProviderImpl_1.id, eventLoggerProviderImpl_2.id);

        // CHANGE THE IMPLEMENTATION OF THE EVENT LOGGER PROVIDER
        eventLoggerProvider.setDelegate(eventLoggerProviderImpl_2);

        // VERIFY THE DELEGATE IMPL HAS CHANGED WHILE THE PARAMS REMAINS UNCHANGED
        EventLoggerMock authenticationEventLoggerImpl_2 = (EventLoggerMock) authenticationEventLogger.delegateEventLogger;
        assertEquals("io.jenkins.authentication", authenticationEventLoggerImpl_2.instrumentationScopeName);
        assertNull(authenticationEventLoggerImpl_2.schemaUrl);
        assertEquals("1.0.0", authenticationEventLoggerImpl_2.instrumentationVersion);
        assertEquals(eventLoggerProviderImpl_2.id, authenticationEventLoggerImpl_2.eventLoggerProviderId);

        EventLoggerMock buildEventLoggerImpl_2 = (EventLoggerMock) buildEventLogger.delegateEventLogger;

        assertEquals("io.jenkins.build", buildEventLoggerImpl_2.instrumentationScopeName);
        assertEquals("https://jenkins.io/build", buildEventLoggerImpl_2.schemaUrl);
        assertNull(buildEventLoggerImpl_2.instrumentationVersion);
        assertEquals(eventLoggerProviderImpl_2.id, buildEventLoggerImpl_2.eventLoggerProviderId);
    }


    static class EventLoggerProviderMock implements EventLoggerProvider {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String id;

        public EventLoggerProviderMock() {
            this.id = "EventLoggerProviderMock-" + ID_SOURCE.incrementAndGet();
        }

        @Override
        public EventLoggerBuilder eventLoggerBuilder(String instrumentationScopeName) {
            return new EventLoggerBuilderMock(instrumentationScopeName, id);
        }

        @Override
        public EventLogger get(String instrumentationScopeName) {
            return new EventLoggerMock(instrumentationScopeName, id);
        }
    }

    static class EventLoggerMock implements EventLogger {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);

        final String instrumentationScopeName;
        final String eventLoggerProviderId;
        final String id;

        final String schemaUrl;
        final String instrumentationVersion;

        public EventLoggerMock(String instrumentationScopeName, String eventLoggerProviderId) {
            this(instrumentationScopeName, eventLoggerProviderId, null, null);
        }
        public EventLoggerMock(String instrumentationScopeName, String eventLoggerProviderId, @Nullable String schemaUrl, @Nullable String instrumentationVersion) {
            this.id = "EventLoggerMock-" + ID_SOURCE.incrementAndGet();
            this.instrumentationScopeName = instrumentationScopeName;
            this.eventLoggerProviderId = eventLoggerProviderId;
            this.schemaUrl = schemaUrl;
            this.instrumentationVersion = instrumentationVersion;
        }

        @Override
        public EventBuilder builder(String eventName) {
            throw new UnsupportedOperationException();
        }
    }

    static class EventLoggerBuilderMock implements EventLoggerBuilder {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String id;
        final String instrumentationScopeName;
        final String eventLoggerProviderId;
        String schemaUrl;
        String instrumentationVersion;


        public EventLoggerBuilderMock(String instrumentationScopeName, String eventLoggerProviderId) {
            this.id = "EventLoggerBuilderMock-" + ID_SOURCE.incrementAndGet();
            this.instrumentationScopeName = instrumentationScopeName;
            this.eventLoggerProviderId = eventLoggerProviderId;
        }

        @Override
        public EventLoggerBuilder setSchemaUrl(String schemaUrl) {
            this.schemaUrl = schemaUrl;
            return this;
        }

        @Override
        public EventLoggerBuilder setInstrumentationVersion(String instrumentationVersion) {
            this.instrumentationVersion = instrumentationVersion;
            return this;
        }

        @Override
        public EventLogger build() {
            return new EventLoggerMock(instrumentationScopeName, eventLoggerProviderId, schemaUrl, instrumentationVersion);
        }
    }
}