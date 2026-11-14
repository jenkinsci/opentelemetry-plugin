/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.TaskListener;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

public class TeeBuildListenerTest {

    @Test
    public void secondaryIsClosedWhenMainCloseThrowsIOException() {
        CloseTrackingTaskListener main = CloseTrackingTaskListener.throwingIOException();
        CloseTrackingTaskListener secondary = CloseTrackingTaskListener.nonThrowing();
        TeeBuildListener tee = new TeeBuildListener(main, secondary);

        assertThrows(IOException.class, tee::close);

        assertTrue(secondary.closed, "secondary listener must be closed even if main.close() throws");
    }

    @Test
    public void secondaryIsClosedWhenMainCloseThrowsRuntimeException() {
        CloseTrackingTaskListener main = CloseTrackingTaskListener.throwingRuntimeException();
        CloseTrackingTaskListener secondary = CloseTrackingTaskListener.nonThrowing();
        TeeBuildListener tee = new TeeBuildListener(main, secondary);

        assertThrows(RuntimeException.class, tee::close);

        assertTrue(
                secondary.closed, "secondary listener must be closed even if main.close() throws a RuntimeException");
    }

    private static class CloseTrackingTaskListener implements TaskListener, Closeable {
        private final IOException ioExceptionOnClose;
        private final RuntimeException runtimeExceptionOnClose;
        private boolean closed;

        private CloseTrackingTaskListener(IOException ioExceptionOnClose, RuntimeException runtimeExceptionOnClose) {
            this.ioExceptionOnClose = ioExceptionOnClose;
            this.runtimeExceptionOnClose = runtimeExceptionOnClose;
        }

        static CloseTrackingTaskListener nonThrowing() {
            return new CloseTrackingTaskListener(null, null);
        }

        static CloseTrackingTaskListener throwingIOException() {
            return new CloseTrackingTaskListener(new IOException("boom"), null);
        }

        static CloseTrackingTaskListener throwingRuntimeException() {
            return new CloseTrackingTaskListener(null, new RuntimeException("boom"));
        }

        @Override
        public PrintStream getLogger() {
            return System.out;
        }

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
