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

/** Build listener that duplicates log output to a primary and secondary listener. */
public final class TeeBuildListener implements BuildListener, AutoCloseable {

    private static final Logger logger = Logger.getLogger(TeeBuildListener.class.getName());

    final TaskListener main;
    final TaskListener secondary;

    /**
     * Creates a tee listener delegating to two task listeners.
     *
     * @param main primary listener
     * @param secondary secondary listener
     */
    public TeeBuildListener(TaskListener main, TaskListener secondary) {
        this.main = main;
        this.secondary = secondary;
    }

    /**
     * Returns a logger that writes to both delegated listeners.
     *
     * @return tee print stream
     */
    @NonNull
    @Override
    public PrintStream getLogger() {
        return new TeePrintStream(main.getLogger(), secondary.getLogger());
    }

    /**
     * Closes both delegated listeners when they implement {@link Closeable}.
     *
     * @throws IOException if an underlying close operation fails
     */
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

    /**
     * Returns a debug representation of this tee listener.
     *
     * @return listener representation
     */
    @Override
    public String toString() {
        return "TeeBuildListener[" + main + "," + secondary + "]";
    }
}
