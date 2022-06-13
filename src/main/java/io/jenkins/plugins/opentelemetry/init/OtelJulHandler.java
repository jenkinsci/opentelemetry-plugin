/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.logs.LogBuilder;
import io.opentelemetry.sdk.logs.LogEmitter;
import io.opentelemetry.sdk.logs.data.Severity;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jenkins.YesNoMaybe;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Inspired by https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/v1.14.0/instrumentation/java-util-logging/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/jul/JavaUtilLoggingHelper.java
 */
@Extension(dynamicLoadable = YesNoMaybe.YES)
public class OtelJulHandler extends Handler implements OtelComponent {

    private final static Logger logger = Logger.getLogger(OtelJulHandler.class.getName());

    private static final Formatter FORMATTER = new AccessibleFormatter();

    private  boolean captureExperimentalAttributes;

    private LogEmitter logEmitter;

    private boolean initialized;

    /**
     * Map the {@link LogRecord} data model onto the {@link LogBuilder}. Unmapped fields include:
     *
     * <ul>
     *   <li>Fully qualified class name - {@link LogRecord#getSourceClassName()}
     *   <li>Fully qualified method name - {@link LogRecord#getSourceMethodName()}
     *   <li>Thread id - {@link LogRecord#getThreadID()}
     * </ul>
     */
    @Override
    public void publish(LogRecord logRecord) {
        LogBuilder logBuilder =  logEmitter.logBuilder();
        // message
        String message = FORMATTER.formatMessage(logRecord);
        if (message != null) {
            logBuilder.setBody(message);
        }

        // time
        // TODO (trask) use getInstant() for more precision on Java 9
        long timestamp = logRecord.getMillis();
        logBuilder.setEpoch(timestamp, TimeUnit.MILLISECONDS);

        // level
        Level level = logRecord.getLevel();
        if (level != null) {
            logBuilder = logBuilder
                .setSeverity(levelToSeverity(level))
                .setSeverityText(logRecord.getLevel().getName());
        }

        AttributesBuilder attributes = Attributes.builder();

        // throwable
        Throwable throwable = logRecord.getThrown();
        if (throwable != null) {
            attributes.put(SemanticAttributes.EXCEPTION_TYPE, throwable.getClass().getName());
            attributes.put(SemanticAttributes.EXCEPTION_MESSAGE, throwable.getMessage());
            StringWriter writer = new StringWriter();
            throwable.printStackTrace(new PrintWriter(writer));
            attributes.put(SemanticAttributes.EXCEPTION_STACKTRACE, writer.toString());
        }

        if (captureExperimentalAttributes) {
            Thread currentThread = Thread.currentThread();
            attributes.put(SemanticAttributes.THREAD_NAME, currentThread.getName());
            attributes.put(SemanticAttributes.THREAD_ID, currentThread.getId());
        }

        logBuilder = logBuilder
            .setAttributes(attributes.build())
            .setContext(Context.current());// span context

        logBuilder.emit();
    }


    private static Severity levelToSeverity(Level level) {
        int lev = level.intValue();
        if (lev <= Level.FINEST.intValue()) {
            return Severity.TRACE;
        }
        if (lev <= Level.FINER.intValue()) {
            return Severity.DEBUG;
        }
        if (lev <= Level.FINE.intValue()) {
            return Severity.DEBUG2;
        }
        if (lev <= Level.CONFIG.intValue()) {
            return Severity.DEBUG3;
        }
        if (lev <= Level.INFO.intValue()) {
            return Severity.INFO;
        }
        if (lev <= Level.WARNING.intValue()) {
            return Severity.WARN;
        }
        if (lev <= Level.SEVERE.intValue()) {
            return Severity.ERROR;
        }
        return Severity.FATAL;
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
        this.captureExperimentalAttributes = configProperties.getBoolean("otel.instrumentation.java-util-logging.experimental-log-attributes", false);
        if (!initialized) {
            Logger.getLogger("").addHandler(this);
            logger.log(Level.FINE, "Otel Logging initialized");
            initialized = true;
        }
    }

    @Override
    public void beforeSdkShutdown() {

    }

    /**
     * Hooking Otel logs is the first thing to initialize
     */
    @Override
    public int ordinal() {
        return Integer.MIN_VALUE;
    }

    // this is just needed for calling formatMessage in abstract super class
    private static class AccessibleFormatter extends Formatter {

        @Override
        public String format(LogRecord record) {
            throw new UnsupportedOperationException();
        }
    }
}
