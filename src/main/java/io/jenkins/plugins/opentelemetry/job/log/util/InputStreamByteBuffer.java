/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Scope;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Readonly {@link ByteBuffer} backed by an {@link InputStream}
 */
public class InputStreamByteBuffer extends ByteBuffer {
    final static Logger logger = Logger.getLogger(InputStreamByteBuffer.class.getName());

    @NonNull
    private final Tracer tracer;

    @NonNull
    final InputStream in;

    public InputStreamByteBuffer(InputStream in, Tracer tracer) {
        this.in = in;
        this.tracer = tracer;
    }

    @Override
    public synchronized long length() {
        Tracer tracer = logger.isLoggable(Level.FINER) ? this.tracer : TracerProvider.noop().get("noop");
        // See system property 'hudson.consoleTailKB'
        // workflow-job-2.41.jar!/org/jenkinsci/plugins/workflow/job/WorkflowRun/console.jelly
        long length = Long.parseLong(System.getProperty("hudson.consoleTailKB", "150")) * 1024; // lower than 150KB -  FIXME verify
        Span span = tracer.spanBuilder("InputStreamByteBuffer.length")
            .setAttribute("response.length", length).startSpan();
        try (Scope scope = span.makeCurrent()) {
            return length;
        } finally {
            span.end();
        }
    }

    @Override
    public InputStream newInputStream() {
        Tracer tracer = logger.isLoggable(Level.FINEST) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("InputStreamByteBuffer.newInputStream")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            return in;
        } finally {
            span.end();
        }
    }

    /**
     * Unsupported byt this readonly {@link ByteBuffer}
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Unsupported byt this readonly {@link ByteBuffer}
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public synchronized void write(int b) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Unsupported byt this readonly {@link ByteBuffer}
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public synchronized void writeTo(OutputStream os) {
        throw new UnsupportedOperationException();
    }

    /**
     * Unsupported byt this readonly {@link ByteBuffer}
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public void write(@NonNull byte[] b) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Unsupported byt this readonly {@link ByteBuffer}
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public void flush() throws IOException {
        throw new UnsupportedOperationException();
    }
}
