/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Scope;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link InputStream} backend by an {@link Iterator}
 */
public class StreamingInputStream extends InputStream {
    private final static Logger logger = Logger.getLogger(StreamingInputStream.class.getName());

    Iterator<String> formattedLogLines;
    final protected Tracer tracer;

    private int cursorOnCurrentLine;
    private byte[] currentLine;
    private long skip;
    final AtomicInteger processedRowsCount = new AtomicInteger();

    public StreamingInputStream(Iterator<String> formattedLogLines, Tracer tracer) {
        this.tracer = tracer;
        this.formattedLogLines = formattedLogLines;
    }

    @Override
    public int read() throws IOException {
        if (currentLine == null) {
            if (cursorOnCurrentLine != 0) {
                throw new IllegalStateException("Current line is null but cursorOnCurrentLine!=0: " + cursorOnCurrentLine);
            }
            currentLine = getNextLine();
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
        return result;
    }

    /**
     * Returns {@code null} if no more data available
     */
    @Nullable
    byte[] getNextLine() throws IOException {
        if (formattedLogLines.hasNext()) {
            String formattedLogLine = formattedLogLines.next();
            this.processedRowsCount.incrementAndGet();
            return (formattedLogLine + "\n").getBytes(StandardCharsets.UTF_8);
        } else {
            return null;
        }
    }

    @Override
    public long skip(long skip) throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("StreamingInputStream.skip")
            .setAttribute("skip", skip).startSpan();
        try (Scope scope = span.makeCurrent()) {
            this.skip = skip;
            // FIXME implement progressive logs rendering
            if (skip > 0) {
                this.formattedLogLines = Collections.singleton("Progressive logs rendering not yet implemented, please refresh page").iterator();
            }
            return skip;
        } finally {
            span.end();
        }
    }

    @Override
    public int available() throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINER) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("StreamingInputStream.available").startSpan();
        try (Scope scope = span.makeCurrent()) {
            if (formattedLogLines.hasNext()) {
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
        if (formattedLogLines instanceof Closeable) {
            ((Closeable) formattedLogLines).close();
        }
    }
}
