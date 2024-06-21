/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerBuilder;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.trace.TracerBuilder;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ReconfigurableLoggerProvider implements LoggerProvider {
    private LoggerProvider delegate;

    private final ConcurrentMap<InstrumentationScope, ReconfigurableLogger> loggers = new ConcurrentHashMap<>();

    public ReconfigurableLoggerProvider() {
        this(LoggerProvider.noop());
    }

    public ReconfigurableLoggerProvider(LoggerProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public LoggerBuilder loggerBuilder(String instrumentationScopeName) {
        return new ReconfigurableLoggerBuilder(delegate.loggerBuilder(instrumentationScopeName), instrumentationScopeName);
    }

    @Override
    public Logger get(String instrumentationScopeName) {
        InstrumentationScope instrumentationScope = new InstrumentationScope(instrumentationScopeName);
        return loggers.computeIfAbsent(instrumentationScope, scope -> new ReconfigurableLogger(delegate.get(instrumentationScopeName)));
    }

    public void setDelegate(LoggerProvider delegate) {
        this.delegate = delegate;
        loggers.forEach((instrumentationScope, reconfigurableTracer) -> {
            LoggerBuilder loggerBuilder = delegate.loggerBuilder(instrumentationScope.instrumentationScopeName);
            Optional.ofNullable(instrumentationScope.instrumentationScopeVersion).ifPresent(loggerBuilder::setInstrumentationVersion);
            Optional.ofNullable(instrumentationScope.schemaUrl).ifPresent(loggerBuilder::setSchemaUrl);
            reconfigurableTracer.setDelegate(loggerBuilder.build());
        });
    }

    @VisibleForTesting
    protected class ReconfigurableLoggerBuilder implements LoggerBuilder {
        LoggerBuilder delegate;
        String instrumentationScopeName;
        String schemaUrl;
        String instrumentationScopeVersion;

        public ReconfigurableLoggerBuilder(LoggerBuilder delegate, String instrumentationScopeName) {
            this.delegate = Objects.requireNonNull(delegate);
            this.instrumentationScopeName = Objects.requireNonNull(instrumentationScopeName);
        }

        @Override
        public LoggerBuilder setSchemaUrl(String schemaUrl) {
            this.schemaUrl = schemaUrl;
            delegate.setSchemaUrl(schemaUrl);
            return this;
        }

        @Override
        public LoggerBuilder setInstrumentationVersion(String instrumentationScopeVersion) {
            this.instrumentationScopeVersion = instrumentationScopeVersion;
            delegate.setInstrumentationVersion(instrumentationScopeVersion);
            return this;
        }

        @Override
        public Logger build() {
            InstrumentationScope instrumentationScope = new InstrumentationScope(instrumentationScopeName, schemaUrl, instrumentationScopeVersion);
            return loggers.computeIfAbsent(instrumentationScope, scope -> new ReconfigurableLogger(delegate.build()));
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableLogger implements Logger {
        Logger delegate;

        public ReconfigurableLogger(Logger delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized LogRecordBuilder logRecordBuilder() {
            return delegate.logRecordBuilder();
        }

        public synchronized void setDelegate(Logger delegate) {
            this.delegate = delegate;
        }
    }
}
