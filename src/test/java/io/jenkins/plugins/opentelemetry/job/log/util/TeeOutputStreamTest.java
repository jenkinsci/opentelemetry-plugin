/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import org.junit.jupiter.api.Test;

public class TeeOutputStreamTest {

    @Test
    public void secondaryIsClosedWhenPrimaryCloseThrows() throws Exception {
        AtomicCloseableOutputStream primary = new AtomicCloseableOutputStream(true);
        AtomicCloseableOutputStream secondary = new AtomicCloseableOutputStream(false);
        TeeOutputStream tee = new TeeOutputStream(primary, secondary);

        assertThrows(IOException.class, tee::close);

        assertTrue(secondary.closed, "secondary stream must be closed even if primary.close() throws");
    }

    private static class AtomicCloseableOutputStream extends OutputStream {
        private final boolean throwOnClose;
        private boolean closed;

        AtomicCloseableOutputStream(boolean throwOnClose) {
            this.throwOnClose = throwOnClose;
        }

        @Override
        public void write(int b) {}

        @Override
        public void close() throws IOException {
            closed = true;
            if (throwOnClose) {
                throw new IOException("boom");
            }
        }
    }
}
