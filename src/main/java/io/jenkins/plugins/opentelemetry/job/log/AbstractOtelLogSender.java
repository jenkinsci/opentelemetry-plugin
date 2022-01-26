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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java
 */
abstract class AbstractOtelLogSender implements BuildListener, Closeable {
    protected final Logger LOGGER = Logger.getLogger(getClass().getName());
    @CheckForNull
    transient PrintStream logger;

    final BuildInfo buildInfo;

    public AbstractOtelLogSender(BuildInfo buildInfo) {
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

    /**
     * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/master/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java#L79
     */
    static final class MasterOtelLogSender extends AbstractOtelLogSender {
        private static final long serialVersionUID = 1;

        public MasterOtelLogSender(BuildInfo buildInfo) {
            super(buildInfo);
        }
    }

    /**
     * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/master/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java#L108
     */
    static final class NodeOtelLogSender extends AbstractOtelLogSender {
        private static final long serialVersionUID = 1;
        final FlowNode node;

        public NodeOtelLogSender(BuildInfo buildInfo, FlowNode node) {
            super(buildInfo);
            this.node = node;
        }

        @Override
        public void close() throws IOException {
            super.close();
            if (JenkinsJVM.isJenkinsJVM()) { // TODO why
                OtelLogStorageFactory.get().close(buildInfo);
            }
        }
    }

    /**
     * TODO support Pipeline Step Context {@link Context} in addition to supporting run root context
     * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java#L162
     */
    private class OtelLogOutputStream extends LineTransformationOutputStream {
        final BuildInfo buildInfo;
        transient LogEmitter logEmitter;
        transient Context context;

        public OtelLogOutputStream(BuildInfo buildInfo) {
            this.buildInfo = buildInfo;
        }

        LogEmitter getLogEmitter() {
            if (logEmitter == null) {
                logEmitter = OpenTelemetrySdkProvider.get().getLogEmitter();
            }
            return logEmitter;
        }

        Context getContext() {
            if (context == null) {
                context = buildInfo.toContext();
            }
            return context;
        }

        @Override
        protected void eol(byte[] bytes, int len) throws IOException {
            if (len == 0) {
                return;
            }
            String message = new String (bytes, 0, len - 1); //remove trailing line feed

            getLogEmitter().logBuilder()
                .setAttributes(buildInfo.toAttributes())
                .setBody(message)
                .setContext(getContext())
                .emit();
            LOGGER.log(Level.INFO, () -> buildInfo + " - emit '" + message + "'"); // FIXME change log level
        }

        @Override
        public void flush() throws IOException {
            // there is no flush concept with the Otel LogEmitter
        }

        @Override
        public void close() throws IOException {
        }
    }
}
