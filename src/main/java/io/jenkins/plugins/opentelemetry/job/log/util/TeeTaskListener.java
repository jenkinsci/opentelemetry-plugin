/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;

import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TeeTaskListener implements TaskListener, AutoCloseable {

    private final static Logger logger = Logger.getLogger(TeeTaskListener.class.getName());

    final TaskListener main;
    final TaskListener secondary;

    final PrintStream printStream;

    public TeeTaskListener(TaskListener main, TaskListener secondary) {
        this.main = main;
        this.secondary = secondary;
        this.printStream = new TeePrintStream(main.getLogger(), secondary.getLogger());
    }

    @NonNull
    @Override
    public PrintStream getLogger() {
        return printStream;
    }

    @Override
    public void close() throws Exception {
        logger.log(Level.FINEST, "close()");

        if (main instanceof AutoCloseable) {
            ((AutoCloseable) main).close();
        }
        if (secondary instanceof AutoCloseable) {
            ((AutoCloseable) secondary).close();
        }
    }

    @Override
    public String toString() {
        return "TeeTaskListener[" + main + "," + secondary + "]";
    }
}
