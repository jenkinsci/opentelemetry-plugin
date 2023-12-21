/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.workflow.log.OutputStreamTaskListener;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TeeOutputStreamTaskListener implements OutputStreamTaskListener, AutoCloseable {

    private static final long serialVersionUID = 1L;

    private final static Logger logger = Logger.getLogger(TeeOutputStreamTaskListener.class.getName());

    private final OutputStreamTaskListener primary;
    private final OutputStreamTaskListener secondary;

    @CheckForNull
    private transient OutputStream outputStream;

    @CheckForNull
    private transient PrintStream printStream;

    public TeeOutputStreamTaskListener(OutputStreamTaskListener primary, OutputStreamTaskListener secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    @NonNull
    @Override
    public synchronized OutputStream getOutputStream() {
        if (outputStream == null) {
            outputStream = new TeeOutputStream(primary.getOutputStream(), secondary.getOutputStream());
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
        logger.log(Level.FINEST, "close()");
        if (primary instanceof AutoCloseable) {
            ((AutoCloseable) primary).close();
        }
        if (secondary instanceof AutoCloseable) {
            ((AutoCloseable) secondary).close();
        }
    }
}
