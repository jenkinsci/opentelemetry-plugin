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
    final boolean complete;
    final protected Tracer tracer;

    private int cursorOnCurrentLine;
    private byte[] currentLine;
    private long skip;
    long readBytes;
    final AtomicInteger processedRowsCount = new AtomicInteger();

    /**
     * @param formattedLogLines the provider of formatted log lines
     * @param complete          {@code true } indicates that the content will be accessed at once and that invocations to `skip`
     *                          should be handled as end of stream.
     */
    public StreamingInputStream(Iterator<String> formattedLogLines, boolean complete, Tracer tracer) {
        this.formattedLogLines = formattedLogLines;
        this.complete = complete;
        this.tracer = tracer;
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
        readBytes++;
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
            .setAttribute("complete", complete)
            .setAttribute("skip", skip).startSpan();
        try (Scope scope = span.makeCurrent()) {
            this.skip = skip;
            if (skip > 0) {
                if (complete) {
                    boolean progressiveStreaming = Boolean.TRUE.equals(PROGRESSIVE_STREAMING.get());
                    span.setAttribute("progressiveStreaming", progressiveStreaming);
                    if (progressiveStreaming) {
                        // progressive rendering of logs, the underlying iterator is capable of handling this
                    } else {
                        // we have already served all the content during the first request
                        // we stop returning content.
                        // happens from
                        // GET /job/:jobFullName/:runNumber/consoleText
                        // |- org.jenkinsci.plugins.workflow.job.WorkflowRun.doConsoleText
                        //   |- org.jenkinsci.plugins.workflow.job.WorkflowRun.getLogText
                        //     |- org.jenkinsci.plugins.workflow.job.WorkflowRun.writeLogTo
                        //        |- hudson.console.AnnotatedLargeText.writeLogTo(long, java.io.OutputStream)
                        // TODO simplify this code
                        this.formattedLogLines = Collections.emptyIterator();
                    }
                }
            }
            readBytes += skip;
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
        Tracer tracer = logger.isLoggable(Level.FINER) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("StreamingInputStream.close")
            .setAttribute("readBytes", readBytes)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            if (formattedLogLines instanceof Closeable) {
                ((Closeable) formattedLogLines).close();
            }
        } finally {
            span.end();
        }
    }

    final static ThreadLocal<Boolean> PROGRESSIVE_STREAMING = new ThreadLocal<>();
    public static void setProgressiveStreaming(){
        PROGRESSIVE_STREAMING.set(Boolean.TRUE);
    }
    public static void unsetProgressiveStreaming(){
        PROGRESSIVE_STREAMING.remove();
    }

}
