/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.logs.LogEmitter;
import io.opentelemetry.sdk.logs.data.Severity;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jenkins.YesNoMaybe;

import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

@Extension(dynamicLoadable = YesNoMaybe.YES)
public class OtelJulHandler extends Handler implements OtelComponent {

    private final static Logger logger = Logger.getLogger(OtelJulHandler.class.getName());

    LogEmitter logEmitter;

    boolean initialized;

    @Override
    public void publish(LogRecord record) {
        logEmitter.logBuilder()
            .setEpoch(record.getMillis(), TimeUnit.MILLISECONDS)
            .setBody(record.getMessage())
            .setSeverity(julLevelToOtelSeverity(record.getLevel()))
            .setAttributes(Attributes.of(
                AttributeKey.stringKey("todo.logger.name"), record.getLoggerName(),
                SemanticAttributes.THREAD_ID, (long) record.getThreadID(),
                AttributeKey.stringKey("todo.source.class.name"), record.getSourceClassName()
            ))
            .emit();
    }

    Severity julLevelToOtelSeverity(Level level) {
        if (Level.SEVERE.equals(level)) {
            return Severity.FATAL;
        } else if (Level.WARNING.equals(level)) {
            return Severity.WARN;
        } else if (Level.INFO.equals(level)) {
            return Severity.INFO;
        } else if (Level.CONFIG.equals(level)) {
            return Severity.DEBUG4;
        } else if (Level.FINE.equals(level)) {
            return Severity.DEBUG;
        } else if (Level.FINER.equals(level)) {
            return Severity.TRACE2;
        } else if (Level.FINEST.equals(level)) {
            return Severity.TRACE;
        } else {
            return Severity.TRACE;
        }
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() throws SecurityException {

    }

    @Override
    public void afterSdkInitialized(Meter meter, LogEmitter logEmitter, Tracer tracer, ConfigProperties configProperties) {
        this.logEmitter = logEmitter;
        if (!initialized) {
            Logger.getLogger("").addHandler(this);
            logger.log(Level.FINE, "Otel Logging initialized");
            initialized = true;
        }
    }

    @Override
    public void beforeSdkShutdown() {

    }


}
