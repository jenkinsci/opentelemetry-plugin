/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Scope;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link InputStream} backend by a {@link LineIterator}
 */
public class LineIteratorInputStream extends InputStream {
    private final static Logger logger = Logger.getLogger(LineIteratorInputStream.class.getName());

    private final LineIterator.LineBytesToLineNumberConverter lineBytesToLineNumberConverter;
    private final LineIterator lines;
    final protected Tracer tracer;

    private int cursorOnCurrentLine;
    private byte[] currentLine;
    private long readLines;
    private long readBytes;

    public LineIteratorInputStream(LineIterator lines, LineIterator.LineBytesToLineNumberConverter lineBytesToLineNumberConverter, Tracer tracer) {
        this.lines = lines;
        this.lineBytesToLineNumberConverter = lineBytesToLineNumberConverter;
        this.tracer = tracer;
    }

    @Override
    public int read() throws IOException {
        if (currentLine == null) {
            if (cursorOnCurrentLine != 0) {
                throw new IllegalStateException("Current line is null but cursorOnCurrentLine!=0: " + cursorOnCurrentLine);
            }
            currentLine = Optional.ofNullable(readLine()).map(line -> line + "\n").map(line -> line.getBytes(StandardCharsets.UTF_8)).orElse(null);
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
    String readLine() throws IOException {
        if (lines.hasNext()) {
            readLines++;
            return lines.next();
        } else {
            return null;
        }
    }

    @Override
    public long skip(long skipBytes) throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("LineIteratorInputStream.skip")
            .setAttribute("skipBytes", skipBytes)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Long skipLogLines = lineBytesToLineNumberConverter.getLogLineFromLogBytes(skipBytes);
            if (skipLogLines == null) {
                span.addEvent("Line Bytes to Line Number conversion not found");
            } else {
                span.setAttribute("skipLines", skipLogLines);
                lines.skipLines(skipLogLines);
                readBytes += skipBytes;
                readLines += skipLogLines;
            }
            return skipBytes;
        } finally {
            span.end();
        }
    }

    @Override
    public int available() throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINER) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("LineIteratorInputStream.available").startSpan();
        try (Scope scope = span.makeCurrent()) {
            if (lines.hasNext()) {
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
        Tracer tracer = logger.isLoggable(Level.FINER) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("LineIteratorInputStream.close")
            .setAttribute("readBytes", readBytes)
            .setAttribute("readLines", readLines)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            lineBytesToLineNumberConverter.putLogBytesToLogLine(readBytes, readLines);
            if (lines instanceof Closeable) {
                ((Closeable) lines).close();
            }
        } finally {
            span.end();
        }
    }

}
