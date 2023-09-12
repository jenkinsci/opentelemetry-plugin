/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import hudson.model.TaskListener;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TeeTaskListener implements TaskListener, Closeable {

    private final static Logger logger = Logger.getLogger(TeeTaskListener.class.getName());

    final TaskListener main;
    final TaskListener secondary;

    public TeeTaskListener(TaskListener main, TaskListener secondary) {
        this.main = main;
        this.secondary = secondary;
    }

    @NonNull
    @Override
    public PrintStream getLogger() {
        try {
            return new TeePrintStream(main.getLogger(), secondary.getLogger());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        logger.log(Level.FINEST, "close()");

        if (main instanceof Closeable) {
            ((Closeable) main).close();
        }
        if (secondary instanceof Closeable) {
            ((Closeable) secondary).close();
        }
    }

    @Override
    public String toString() {
        return "TeeTaskListener[" + main + "," + secondary + "]";
    }
}
