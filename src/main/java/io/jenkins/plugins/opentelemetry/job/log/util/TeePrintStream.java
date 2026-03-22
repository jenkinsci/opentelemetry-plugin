/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * A {@link PrintStream} that writes all output to two print streams simultaneously.
 */
public class TeePrintStream extends PrintStream {

    final PrintStream secondary;

    /**
     * Creates a tee print stream that mirrors all writes to {@code primary} and {@code secondary}.
     *
     * @param primary   the primary (delegate) print stream
     * @param secondary the secondary print stream
     */
    public TeePrintStream(@NonNull PrintStream primary, @NonNull PrintStream secondary) {
        super(primary, false, StandardCharsets.UTF_8);
        this.secondary = secondary;
    }

    /**
     * Flushes both the primary and the secondary print streams.
     */
    @Override
    public void flush() {
        super.flush();
        secondary.flush();
    }

    /**
     * Closes both print streams, collecting any exceptions from both.
     */
    @Override
    public void close() {
        RuntimeException e1 = null;
        try {
            super.close();
        } catch (RuntimeException e) {
            e1 = e;
        }
        RuntimeException e2 = null;
        try {
            secondary.close();
        } catch (RuntimeException e) {
            e2 = e;
        }
        if (e1 != null && e2 != null) {
            throw new RuntimeException("Both print streams failed to close: primary=" + e1 + ", secondary=" + e2, e1);
        } else if (e1 != null) {
            throw e1;
        } else if (e2 != null) {
            throw e2;
        }
    }

    /**
     * Returns {@code true} when both the primary and secondary streams have reported an error.
     *
     * @return {@code true} when both streams are in error state
     */
    @Override
    public boolean checkError() {
        return super.checkError() && secondary.checkError();
    }

    /**
     * Writes a single byte to both streams.
     *
     * @param b the byte to write
     */
    @Override
    public void write(int b) {
        super.write(b);
        secondary.write(b);
    }

    /**
     * Writes a byte-array slice to both streams.
     *
     * @param buf the byte buffer
     * @param off start offset
     * @param len number of bytes
     */
    @Override
    public void write(@NonNull byte[] buf, int off, int len) {
        super.write(buf, off, len);
        secondary.write(buf, off, len);
    }
}
