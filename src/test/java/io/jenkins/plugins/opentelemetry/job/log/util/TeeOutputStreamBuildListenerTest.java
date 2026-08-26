/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.BuildListener;
import java.io.OutputStream;
import java.io.PrintStream;
import org.jenkinsci.plugins.workflow.log.OutputStreamTaskListener;
import org.junit.jupiter.api.Test;

public class TeeOutputStreamBuildListenerTest {

    @Test
    public void secondaryIsClosedWhenPrimaryCloseThrows() {
        AutoCloseableBuildListener primary = new AutoCloseableBuildListener(true);
        AutoCloseableBuildListener secondary = new AutoCloseableBuildListener(false);
        TeeOutputStreamBuildListener tee = new TeeOutputStreamBuildListener(primary, secondary);

        assertThrows(Exception.class, tee::close);

        assertTrue(secondary.closed, "secondary listener must be closed even if primary.close() throws");
    }

    private static class AutoCloseableBuildListener implements BuildListener, OutputStreamTaskListener, AutoCloseable {
        private final boolean throwOnClose;
        private boolean closed;

        AutoCloseableBuildListener(boolean throwOnClose) {
            this.throwOnClose = throwOnClose;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public PrintStream getLogger() {
            return System.out;
        }

        @Override
        public void close() throws Exception {
            closed = true;
            if (throwOnClose) {
                throw new Exception("boom");
            }
        }
    }
}
