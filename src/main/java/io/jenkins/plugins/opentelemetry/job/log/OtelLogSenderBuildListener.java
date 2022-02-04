/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.model.BuildListener;
import jenkins.util.JenkinsJVM;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * * Initialized a {@link OtelLogOutputStream} to send the events that happen during a build.
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java
 */
class OtelLogSenderBuildListener implements BuildListener, Closeable {
    protected final Logger LOGGER = Logger.getLogger(getClass().getName());
    final BuildInfo buildInfo;
    final Map<String, String> context;
    @CheckForNull
    transient PrintStream logger;

    public OtelLogSenderBuildListener(@Nonnull BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
        this.context = buildInfo.getContext();
    }

    public OtelLogSenderBuildListener(@Nonnull BuildInfo buildInfo, @Nonnull FlowNode node) {
        buildInfo.setFlowNode(node.getId());
        this.buildInfo = buildInfo;
        this.context = buildInfo.getContext();
    }

    @Nonnull
    @Override
    public synchronized final PrintStream getLogger() {
        if (logger == null) {
            try {
                logger = new PrintStream(new OtelLogOutputStream(buildInfo, context), false, "UTF-8");
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
        if (JenkinsJVM.isJenkinsJVM()) { // TODO why
            OtelLogStorageFactory.get().close(buildInfo);
        }
    }
}
