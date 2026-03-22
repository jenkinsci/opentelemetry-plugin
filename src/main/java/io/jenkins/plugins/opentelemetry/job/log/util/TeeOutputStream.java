/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An {@link OutputStream} that writes all bytes to two output streams simultaneously.
 */
public class TeeOutputStream extends OutputStream {

    final OutputStream primary;
    final OutputStream secondary;

    /**
     * Creates a tee output stream that mirrors all writes to {@code primary} and {@code secondary}.
     *
     * @param primary   the primary output stream
     * @param secondary the secondary output stream
     */
    public TeeOutputStream(OutputStream primary, OutputStream secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    /**
     * Writes a single byte to both streams.
     *
     * @param b the byte to write
     * @throws IOException if either stream throws
     */
    @Override
    public void write(int b) throws IOException {
        primary.write(b);
        secondary.write(b);
    }

    /**
     * Writes a byte array to both streams.
     *
     * @param b the bytes to write
     * @throws IOException if either stream throws
     */
    @Override
    public void write(byte[] b) throws IOException {
        primary.write(b);
        secondary.write(b);
    }

    /**
     * Writes a byte-array slice to both streams.
     *
     * @param b   the byte buffer
     * @param off start offset
     * @param len number of bytes
     * @throws IOException if either stream throws
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        primary.write(b, off, len);
        secondary.write(b, off, len);
    }

    /**
     * Flushes both streams.
     *
     * @throws IOException if either stream throws
     */
    @Override
    public void flush() throws IOException {
        primary.flush();
        secondary.flush();
    }

    /**
     * Closes both streams.
     *
     * @throws IOException if either stream throws
     */
    @Override
    public void close() throws IOException {
        primary.close();
        secondary.close();
    }
}
