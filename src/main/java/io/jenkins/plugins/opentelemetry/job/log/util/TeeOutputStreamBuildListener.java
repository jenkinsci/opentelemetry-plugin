/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import hudson.model.BuildListener;
import org.jenkinsci.plugins.workflow.log.OutputStreamTaskListener;

import javax.annotation.Nonnull;
import java.io.OutputStream;
import java.io.PrintStream;

public class TeeOutputStreamBuildListener implements BuildListener, OutputStreamTaskListener, AutoCloseable {

    final BuildListener primary;

    final BuildListener secondary;

    final OutputStream outputStream;

    final PrintStream printStream;

    public TeeOutputStreamBuildListener(BuildListener primary, BuildListener secondary) {
        if (!(primary instanceof OutputStreamTaskListener)) {
            throw new ClassCastException("Primary is not an instance of OutputStreamTaskListener: " + primary);
        }
        if (!(secondary instanceof OutputStreamTaskListener)) {
            throw new ClassCastException("Secondary is not an instance of OutputStreamTaskListener: " + primary);
        }
        this.primary = primary;
        this.secondary = secondary;

        this.outputStream = new TeeOutputStream(((OutputStreamTaskListener) primary).getOutputStream(), ((OutputStreamTaskListener) secondary).getOutputStream());
        this.printStream = new TeePrintStream(primary.getLogger(), secondary.getLogger());
    }

    @Nonnull
    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Nonnull
    @Override
    public PrintStream getLogger() {
        return printStream;
    }

    @Override
    public void close() throws Exception {
        if (primary instanceof AutoCloseable) {
            ((AutoCloseable) primary).close();
        }
        if (secondary instanceof AutoCloseable) {
            ((AutoCloseable) secondary).close();
        }
    }
}
