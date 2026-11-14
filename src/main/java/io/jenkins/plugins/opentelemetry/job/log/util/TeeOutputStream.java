/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import java.io.IOException;
import java.io.OutputStream;

public class TeeOutputStream extends OutputStream {

    final OutputStream primary;
    final OutputStream secondary;

    public TeeOutputStream(OutputStream primary, OutputStream secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public void write(int b) throws IOException {
        primary.write(b);
        secondary.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        primary.write(b);
        secondary.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        primary.write(b, off, len);
        secondary.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        primary.flush();
        secondary.flush();
    }

    @Override
    public void close() throws IOException {
        Throwable exception = null;
        try {
            primary.close();
        } catch (IOException | RuntimeException e) {
            exception = e;
        }
        try {
            secondary.close();
        } catch (IOException | RuntimeException e) {
            if (exception == null) {
                exception = e;
            } else {
                exception.addSuppressed(e);
            }
        }
        if (exception instanceof IOException) {
            throw (IOException) exception;
        } else if (exception instanceof RuntimeException) {
            throw (RuntimeException) exception;
        }
    }
}
