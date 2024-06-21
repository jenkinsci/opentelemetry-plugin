/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.api.incubator.events.EventBuilder;
import io.opentelemetry.api.incubator.events.EventLogger;
import io.opentelemetry.api.incubator.events.EventLoggerBuilder;
import io.opentelemetry.api.incubator.events.EventLoggerProvider;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * <p>
 * A {@link EventLoggerProvider} that allows to reconfigure the {@link EventLogger}s.
 * </p>
 * <p>
 * We need reconfigurability because Jenkins supports changing the configuration of the OpenTelemetry params at runtime.
 * All instantiated eventLoggers are reconfigured when the configuration changes, when
 * {@link ReconfigurableEventLoggerProvider#setDelegate(EventLoggerProvider)} is invoked.
 * </p>
 */
class ReconfigurableEventLoggerProvider implements EventLoggerProvider {

    private final ConcurrentMap<InstrumentationScope, ReconfigurableEventLogger> eventLoggers = new ConcurrentHashMap<>();
    private EventLoggerProvider delegate = EventLoggerProvider.noop();

    @Override
    public EventLoggerBuilder eventLoggerBuilder(String instrumentationScopeName) {
        return new ReconfigurableEventLoggerBuilder(delegate.eventLoggerBuilder(instrumentationScopeName), instrumentationScopeName);
    }

    @Override
    public EventLogger get(String instrumentationScopeName) {
        return eventLoggers.computeIfAbsent(new InstrumentationScope(instrumentationScopeName), key -> new ReconfigurableEventLogger(delegate.get(key.instrumentationScopeName)));
    }

    public void setDelegate(EventLoggerProvider delegateEventLoggerBuilder) {
        this.delegate = delegateEventLoggerBuilder;
        eventLoggers.forEach((key, reconfigurableEventLogger) -> {
            EventLoggerBuilder eventLoggerBuilder = delegateEventLoggerBuilder.eventLoggerBuilder(key.instrumentationScopeName);
            Optional.ofNullable(key.schemaUrl).ifPresent(eventLoggerBuilder::setSchemaUrl);
            Optional.ofNullable(key.instrumentationScopeVersion).ifPresent(eventLoggerBuilder::setInstrumentationVersion);
            reconfigurableEventLogger.delegateEventLogger = eventLoggerBuilder.build();
        });
    }

    @VisibleForTesting
    protected static class ReconfigurableEventLogger implements EventLogger {
        EventLogger delegateEventLogger;

        public ReconfigurableEventLogger(EventLogger delegateEventLogger) {
            this.delegateEventLogger = Objects.requireNonNull(delegateEventLogger);
        }

        @Override
        public EventBuilder builder(String eventName) {
            return delegateEventLogger.builder(eventName);
        }
    }

    @VisibleForTesting
    protected class ReconfigurableEventLoggerBuilder implements EventLoggerBuilder {
        EventLoggerBuilder delegate;
        String instrumentationScopeName;
        String schemaUrl;
        String instrumentationScopeVersion;

        public ReconfigurableEventLoggerBuilder(EventLoggerBuilder delegate, String instrumentationScopeName) {
            this.delegate = Objects.requireNonNull(delegate);
            this.instrumentationScopeName = Objects.requireNonNull(instrumentationScopeName);
        }

        @Override
        public EventLoggerBuilder setSchemaUrl(String schemaUrl) {
            delegate.setSchemaUrl(schemaUrl);
            this.schemaUrl = schemaUrl;
            return this;
        }

        @Override
        public EventLoggerBuilder setInstrumentationVersion(String instrumentationScopeVersion) {
            delegate.setInstrumentationVersion(instrumentationScopeVersion);
            this.instrumentationScopeVersion = instrumentationScopeVersion;
            return this;
        }

        @Override
        public EventLogger build() {
            InstrumentationScope key = new InstrumentationScope(instrumentationScopeName, schemaUrl, instrumentationScopeVersion);
            return eventLoggers.computeIfAbsent(key, k -> new ReconfigurableEventLogger(delegate.build()));
        }
    }
}
