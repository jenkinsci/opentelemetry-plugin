/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.model.BuildListener;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Initialized a {@link OtelLogOutputStream} to send the events that happen during a build.
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java
 */
class OtelLogSender implements BuildListener, Closeable {
    protected final Logger LOGGER = Logger.getLogger(OtelLogSender.class.getName());
    final BuildInfo buildInfo;
    @CheckForNull
    transient PrintStream logger;

    public OtelLogSender(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    @Nonnull
    @Override
    public synchronized final PrintStream getLogger() {
        if (logger == null) {
            try {
                logger = new PrintStream(new OtelLogOutputStream(buildInfo), false, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError(e);
            }
        }

        return logger;
    }

    @Override
    public void close() throws IOException {
        if (logger != null) {
            LOGGER.log(Level.FINE, () -> getClass().getName() + "#close(" + buildInfo + ")");
            logger = null;
        }
    }


}
