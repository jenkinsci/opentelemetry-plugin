/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.workflow.log.OutputStreamTaskListener;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TeeOutputStreamTaskListener implements OutputStreamTaskListener, AutoCloseable {
    private final static Logger logger = Logger.getLogger(TeeOutputStreamTaskListener.class.getName());

    final OutputStreamTaskListener primary;
    final OutputStreamTaskListener secondary;

    final OutputStream outputStream;

    final PrintStream printStream;

    public TeeOutputStreamTaskListener(OutputStreamTaskListener primary, OutputStreamTaskListener secondary) {
        this.primary = primary;
        this.secondary = secondary;

        OutputStream primaryOutputStream = primary.getOutputStream();
        OutputStream secondaryOutputStream = secondary.getOutputStream();
        if (primaryOutputStream instanceof PrintStream && secondaryOutputStream instanceof PrintStream) {
            this.outputStream = new TeePrintStream((PrintStream) primaryOutputStream, (PrintStream) secondaryOutputStream);
        } else {
            this.outputStream = new TeeOutputStream(primaryOutputStream, secondaryOutputStream);
        }
        this.printStream = new TeePrintStream(primary.getLogger(), secondary.getLogger());
    }

    @NonNull
    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @NonNull
    @Override
    public PrintStream getLogger() {
        return printStream;
    }

    @Override
    public void close() throws Exception {
        logger.log(Level.FINEST, "close()");
        if (primary instanceof AutoCloseable) {
            ((AutoCloseable) primary).close();
        }
        if (secondary instanceof AutoCloseable) {
            ((AutoCloseable) secondary).close();
        }
    }
}
