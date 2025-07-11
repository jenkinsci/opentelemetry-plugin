/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.jenkins.plugins.opentelemetry.job.log.LogLine;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Scope;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link InputStream} backed by a {@link LogLineIterator}
 */
public class LogLineIteratorInputStream<Id> extends InputStream {
    private static final Logger logger = Logger.getLogger(LogLineIteratorInputStream.class.getName());

    private final LogLineIterator.LogLineBytesToLogLineIdMapper<Id> logLineBytesToLogLineIdConverter;
    private final LogLineIterator<Id> logLines;
    protected final Tracer tracer;

    private int cursorOnCurrentLine;
    private byte[] currentLine;
    private long readBytes;
    private Id lastLogLineId;

    public LogLineIteratorInputStream(
            LogLineIterator<Id> logLines,
            LogLineIterator.LogLineBytesToLogLineIdMapper<Id> logLineBytesToLogLineIdConverter,
            Tracer tracer) {
        this.logLines = logLines;
        this.logLineBytesToLogLineIdConverter = logLineBytesToLogLineIdConverter;
        this.tracer = tracer;
    }

    @Override
    public int read() throws IOException {
        if (currentLine == null) {
            if (cursorOnCurrentLine != 0) {
                throw new IllegalStateException(
                        "Current line is null but cursorOnCurrentLine!=0: " + cursorOnCurrentLine);
            }
            currentLine = Optional.ofNullable(readLine())
                    .map(line -> (line.getMessage() + "\n").getBytes(StandardCharsets.UTF_8))
                    .orElse(null);
            if (currentLine == null) {
                return -1;
            }
        }
        if (cursorOnCurrentLine > currentLine.length) {
            throw new IllegalStateException();
        }
        int result = currentLine[cursorOnCurrentLine++];
        if (cursorOnCurrentLine == currentLine.length) {
            currentLine = null;
            cursorOnCurrentLine = 0;
        }
        readBytes++;
        return result;
    }

    /**
     * Returns {@code null} if no more data available
     */
    @Nullable
    LogLine<Id> readLine() {
        if (logLines.hasNext()) {
            LogLine<Id> logLine = logLines.next();
            lastLogLineId = logLine.getId();
            return logLine;
        } else {
            return null;
        }
    }

    @Override
    public long skip(long skipBytes) throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINE)
                ? this.tracer
                : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("LogLineIteratorInputStream.skip")
                .setAttribute("skipBytes", skipBytes)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Optional<Id> logLineId =
                    Optional.ofNullable(logLineBytesToLogLineIdConverter.getLogLineIdFromLogBytes(skipBytes));
            logLineId.ifPresentOrElse(
                    id -> {
                        span.setAttribute("previousLastLogLineId", String.valueOf(this.lastLogLineId));
                        span.setAttribute("lastLogLineId", String.valueOf(id));
                        logLines.skipLines(id);
                        readBytes += skipBytes;
                        this.lastLogLineId = id;
                    },
                    () -> span.addEvent("LogLine Bytes to LogLine Id conversion not found"));
            return skipBytes;
        } finally {
            span.end();
        }
    }

    @Override
    public int available() throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINER)
                ? this.tracer
                : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("LogLineIteratorInputStream.available").startSpan();
        try (Scope scope = span.makeCurrent()) {
            if (logLines.hasNext()) {
                return 1;
            } else {
                return 0;
            }
        } finally {
            span.end();
        }
    }

    @Override
    public void close() throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINER)
                ? this.tracer
                : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("LogLineIteratorInputStream.close")
                .setAttribute("readBytes", readBytes)
                .setAttribute("lastLogLineId", String.valueOf(lastLogLineId))
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            logLineBytesToLogLineIdConverter.putLogBytesToLogLineId(readBytes, lastLogLineId);
            if (logLines instanceof Closeable) {
                ((Closeable) logLines).close();
            }
        } finally {
            span.end();
        }
    }
}
