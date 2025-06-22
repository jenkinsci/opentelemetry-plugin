/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TeeBuildListener implements BuildListener, AutoCloseable {

    private static final Logger logger = Logger.getLogger(TeeBuildListener.class.getName());

    final TaskListener main;
    final TaskListener secondary;

    public TeeBuildListener(TaskListener main, TaskListener secondary) {
        this.main = main;
        this.secondary = secondary;
    }

    @NonNull
    @Override
    public PrintStream getLogger() {
        return new TeePrintStream(main.getLogger(), secondary.getLogger());
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
        return "TeeBuildListener[" + main + "," + secondary + "]";
    }
}
