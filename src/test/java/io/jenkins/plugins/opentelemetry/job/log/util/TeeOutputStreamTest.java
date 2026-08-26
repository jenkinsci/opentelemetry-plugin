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
    public void secondaryIsClosedWhenPrimaryCloseThrowsIOException() throws Exception {
        CloseTrackingOutputStream primary = CloseTrackingOutputStream.throwingIOException();
        CloseTrackingOutputStream secondary = CloseTrackingOutputStream.nonThrowing();
        TeeOutputStream tee = new TeeOutputStream(primary, secondary);

        assertThrows(IOException.class, tee::close);

        assertTrue(secondary.closed, "secondary stream must be closed even if primary.close() throws");
    }

    @Test
    public void secondaryIsClosedWhenPrimaryCloseThrowsRuntimeException() {
        CloseTrackingOutputStream primary = CloseTrackingOutputStream.throwingRuntimeException();
        CloseTrackingOutputStream secondary = CloseTrackingOutputStream.nonThrowing();
        TeeOutputStream tee = new TeeOutputStream(primary, secondary);

        assertThrows(RuntimeException.class, tee::close);

        assertTrue(
                secondary.closed, "secondary stream must be closed even if primary.close() throws a RuntimeException");
    }

    private static class CloseTrackingOutputStream extends OutputStream {
        private final IOException ioExceptionOnClose;
        private final RuntimeException runtimeExceptionOnClose;
        private boolean closed;

        private CloseTrackingOutputStream(IOException ioExceptionOnClose, RuntimeException runtimeExceptionOnClose) {
            this.ioExceptionOnClose = ioExceptionOnClose;
            this.runtimeExceptionOnClose = runtimeExceptionOnClose;
        }

        static CloseTrackingOutputStream nonThrowing() {
            return new CloseTrackingOutputStream(null, null);
        }

        static CloseTrackingOutputStream throwingIOException() {
            return new CloseTrackingOutputStream(new IOException("boom"), null);
        }

        static CloseTrackingOutputStream throwingRuntimeException() {
            return new CloseTrackingOutputStream(null, new RuntimeException("boom"));
        }

        @Override
        public void write(int b) {}

        @Override
        public void close() throws IOException {
            closed = true;
            if (ioExceptionOnClose != null) {
                throw ioExceptionOnClose;
            }
            if (runtimeExceptionOnClose != null) {
                throw runtimeExceptionOnClose;
            }
        }
    }
}
