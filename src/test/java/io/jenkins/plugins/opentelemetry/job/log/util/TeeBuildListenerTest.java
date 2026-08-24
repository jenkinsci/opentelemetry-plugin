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
    public void secondaryIsClosedWhenMainCloseThrows() {
        CloseableTaskListener main = new CloseableTaskListener(true);
        CloseableTaskListener secondary = new CloseableTaskListener(false);
        TeeBuildListener tee = new TeeBuildListener(main, secondary);

        assertThrows(IOException.class, tee::close);

        assertTrue(secondary.closed, "secondary listener must be closed even if main.close() throws");
    }

    private static class CloseableTaskListener implements TaskListener, Closeable {
        private final boolean throwOnClose;
        private boolean closed;

        CloseableTaskListener(boolean throwOnClose) {
            this.throwOnClose = throwOnClose;
        }

        @Override
        public PrintStream getLogger() {
            return System.out;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (throwOnClose) {
                throw new IOException("boom");
            }
        }
    }
}
