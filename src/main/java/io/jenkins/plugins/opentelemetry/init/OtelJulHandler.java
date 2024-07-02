/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.OpenTelemetryLifecycleListener;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import jenkins.YesNoMaybe;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Inspired by https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/v1.14.0/instrumentation/java-util-logging/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/jul/JavaUtilLoggingHelper.java
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true, ordinal = Integer.MAX_VALUE - 10 /* very high but OTel Config should happen before*/)
public class OtelJulHandler extends Handler implements OpenTelemetryLifecycleListener {

    private final static Logger logger = Logger.getLogger(OtelJulHandler.class.getName());

    private static final Formatter FORMATTER = new AccessibleFormatter();

    private boolean captureExperimentalAttributes;

    private LoggerProvider loggerProvider;

    @Inject
    protected JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    public OtelJulHandler() {
        try {
            // protect against init errors. https://github.com/jenkinsci/opentelemetry-plugin/issues/622
            Context context = Context.current();
            logger.log(Level.FINER, () -> "OtelJulHandler initialization - context: " + context);
        } catch (NoClassDefFoundError | RuntimeException e) {
            logger.log(Level.WARNING, "Exception initializing OPenTelemetry SDK logging apis, disable OtelJulHandler");
            throw e;
        }
    }

    /**
     * Circuit breaker
     */
    private boolean disabled = false;

    /**
     * Map the {@link LogRecord} data model onto the {@link io.opentelemetry.api.logs.LogRecordBuilder}. Unmapped fields include:
     *
     * <ul>
     *   <li>Fully qualified class name - {@link LogRecord#getSourceClassName()}
     *   <li>Fully qualified method name - {@link LogRecord#getSourceMethodName()}
     * </ul>
     */
    @Override
    public void publish(LogRecord logRecord) {
        if (disabled) {
            return;
        }
        try {
            String instrumentationName = logRecord.getLoggerName();
            if (instrumentationName == null || instrumentationName.isEmpty()) {
                instrumentationName = "ROOT";
            }
            LogRecordBuilder logBuilder = loggerProvider.get(instrumentationName).logRecordBuilder();
            // message
            String message = FORMATTER.formatMessage(logRecord);
            if (message != null) {
                logBuilder = logBuilder.setBody(message);
            }

            // time
            Instant timestamp = logRecord.getInstant();
            logBuilder = logBuilder.setTimestamp(timestamp);

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
                attributes.put(ExceptionAttributes.EXCEPTION_TYPE, throwable.getClass().getName());
                attributes.put(ExceptionAttributes.EXCEPTION_MESSAGE, throwable.getMessage());
                StringWriter writer = new StringWriter();
                throwable.printStackTrace(new PrintWriter(writer));
                attributes.put(ExceptionAttributes.EXCEPTION_STACKTRACE, writer.toString());
            }

            if (captureExperimentalAttributes) {
                Thread currentThread = Thread.currentThread();
                attributes.put(ThreadIncubatingAttributes.THREAD_NAME, currentThread.getName());
                attributes.put(ThreadIncubatingAttributes.THREAD_ID, currentThread.getId());
            } else {
                attributes.put(ThreadIncubatingAttributes.THREAD_ID, logRecord.getThreadID());
            }

            logBuilder = logBuilder
                .setAllAttributes(attributes.build())
                .setContext(Context.current());// span context

            logBuilder.emit();
        } catch (RuntimeException e) {
            // directly use `System.err` rather than the `logger` 
            // because the OTelJulHandler is a handler of this logger, risking an infinite loop
            System.err.println("Exception sending logs to OTLP endpoint, disable OTelJulHandler");
            e.printStackTrace();
            disabled = true;
        }
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

    @PostConstruct
    public void postConstruct() {
        this.loggerProvider = jenkinsControllerOpenTelemetry.getLogsBridge();
        this.captureExperimentalAttributes = jenkinsControllerOpenTelemetry.getConfig().getBoolean("otel.instrumentation.java-util-logging.experimental-log-attributes", false);
        Logger.getLogger("").addHandler(this);
        logger.log(Level.FINE, "Otel java.util.logging bridge initialized");
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
