/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.BuildListener;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serial;
import org.jenkinsci.plugins.workflow.log.OutputStreamTaskListener;

public class TeeOutputStreamBuildListener implements BuildListener, OutputStreamTaskListener, AutoCloseable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final BuildListener primary;

    private final BuildListener secondary;

    private transient OutputStream outputStream;

    private transient PrintStream printStream;

    public TeeOutputStreamBuildListener(BuildListener primary, BuildListener secondary) {
        if (!(primary instanceof OutputStreamTaskListener)) {
            throw new ClassCastException("Primary is not an instance of OutputStreamTaskListener: " + primary);
        }
        if (!(secondary instanceof OutputStreamTaskListener)) {
            throw new ClassCastException("Secondary is not an instance of OutputStreamTaskListener: " + primary);
        }
        this.primary = primary;
        this.secondary = secondary;
    }

    @NonNull
    @Override
    public synchronized OutputStream getOutputStream() {
        if (outputStream == null) {
            outputStream = new TeeOutputStream(
                    ((OutputStreamTaskListener) primary).getOutputStream(),
                    ((OutputStreamTaskListener) secondary).getOutputStream());
        }
        return outputStream;
    }

    @NonNull
    @Override
    public synchronized PrintStream getLogger() {
        if (printStream == null) {
            printStream = new TeePrintStream(primary.getLogger(), secondary.getLogger());
        }
        return printStream;
    }

    @Override
    public void close() throws Exception {
        Exception exception = null;
        if (primary instanceof AutoCloseable) {
            try {
                ((AutoCloseable) primary).close();
            } catch (Exception e) {
                exception = e;
            }
        }
        if (secondary instanceof AutoCloseable) {
            try {
                ((AutoCloseable) secondary).close();
            } catch (Exception e) {
                if (exception == null) {
                    exception = e;
                } else {
                    exception.addSuppressed(e);
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }
}
