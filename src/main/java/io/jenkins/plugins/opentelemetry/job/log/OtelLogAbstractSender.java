/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.console.LineTransformationOutputStream;
import hudson.model.BuildListener;
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
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java
 */
abstract class OtelLogAbstractSender implements BuildListener, Closeable {
    protected final Logger LOGGER = Logger.getLogger(getClass().getName());
    @CheckForNull
    transient PrintStream logger;

    final LogEmitter logEmitter; // FIXME check if transient is needed. If so, what deserialization strategy
    final BuildInfo buildInfo;

    public OtelLogAbstractSender(BuildInfo buildInfo, LogEmitter logEmitter) {
        this.logEmitter = logEmitter;
        this.buildInfo = buildInfo;
    }

    @Nonnull
    @Override
    public synchronized final PrintStream getLogger() {
        if (logger == null) {
            try {
                logger = new PrintStream(new OtelLogOutputStream(buildInfo, logEmitter), false, "UTF-8");
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
    static final class MasterSender extends OtelLogAbstractSender {
        private static final long serialVersionUID = 1;

        public MasterSender(BuildInfo buildInfo, LogEmitter logEmitter) {
            super(buildInfo, logEmitter);
        }
    }

    /**
     * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/master/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java#L108
     */
    static final class NodeSender extends OtelLogAbstractSender {
        private static final long serialVersionUID = 1;
        final FlowNode node;

        public NodeSender(BuildInfo buildInfo, FlowNode node, LogEmitter logEmitter) {
            super(buildInfo, logEmitter);
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
     * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java#L162
     */
    private class OtelLogOutputStream extends LineTransformationOutputStream {
        final BuildInfo buildInfo;
        final LogEmitter logEmitter;

        public OtelLogOutputStream(BuildInfo buildInfo, LogEmitter logEmitter) {
            this.buildInfo = buildInfo;
            this.logEmitter = logEmitter;
        }

        @Override
        protected void eol(byte[] bytes, int len) throws IOException {
            String logLine = new String(bytes, 0, len, StandardCharsets.UTF_8);
            logEmitter.logBuilder()
                .setAttributes(buildInfo.toAttributes())
                .setBody(logLine)
                // .setContext() TODO trace correlation
                .emit();
            LOGGER.log(Level.INFO, () -> buildInfo + " - emit " + logLine); // FIXME change log level
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
