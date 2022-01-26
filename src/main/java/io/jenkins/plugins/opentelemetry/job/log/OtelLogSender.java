/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.console.LineTransformationOutputStream;
import hudson.console.PlainTextConsoleOutputStream;
import hudson.model.BuildListener;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogEmitter;
import jenkins.util.JenkinsJVM;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Initialized a {@link io.jenkins.plugins.elasticstacklogs.OutputStream} to send the events that happen during a build.
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java
 */
class OtelLogSender implements BuildListener, Closeable {
    protected final Logger LOGGER = Logger.getLogger(getClass().getName());
    @CheckForNull
    transient PrintStream logger;

    final BuildInfo buildInfo;

    public OtelLogSender(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    @Nonnull
    @Override
    public synchronized final PrintStream getLogger() {
        if (logger == null) {
            try {
                logger = new PrintStream(new PlainTextConsoleOutputStream(new OtelLogOutputStream(buildInfo)), false, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError(e);
            }
        }

        return logger;
    }

    @Override
    public void close() throws IOException {
        if (logger != null) {
            LOGGER.log(Level.INFO, () -> getClass().getName() + "#close(" + buildInfo + ")");
            logger = null;
        }
    }






}
