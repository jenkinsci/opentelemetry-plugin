/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.model.BuildListener;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.opentelemetry.sdk.logs.LogEmitter;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
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
 * {@link BuildListener} to replace the standard {@link BuildListener#getLogger()} by the {@link OtelLogOutputStream}.
 * <p>
 * {@link OtelLogSenderBuildListener} is instantiated both on the Jenkins Controller and on the Jenkins Agents with different implementations:
 * - On the Jenkins Controller as a {@link OtelLogSenderBuildListenerOnController} instance
 * - On Jenkins Agents as a {@link OtelLogSenderBuildListenerOnAgent} instance
 * <p>
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java
 */
abstract class OtelLogSenderBuildListener implements BuildListener, Closeable {

    static final long serialVersionUID = 1L;

    protected final static Logger LOGGER = Logger.getLogger(OtelLogSenderBuildListener.class.getName());
    final BuildInfo buildInfo;
    final Map<String, String> w3cTraceContext;
    @CheckForNull
    transient PrintStream logger;

    public OtelLogSenderBuildListener(@Nonnull BuildInfo buildInfo) {
        this.buildInfo = new BuildInfo(buildInfo);
        this.w3cTraceContext = buildInfo.getW3cTraceContext();
    }

    public OtelLogSenderBuildListener(@Nonnull BuildInfo buildInfo, @Nonnull FlowNode node) {
        this.buildInfo = new BuildInfo(buildInfo);
        this.buildInfo.setFlowNode(node.getId());
        this.w3cTraceContext = buildInfo.getW3cTraceContext();
    }

    @Nonnull
    @Override
    public synchronized final PrintStream getLogger() {
        if (logger == null) {
            try {
                logger = new PrintStream(new OtelLogOutputStream(buildInfo, w3cTraceContext, getLogEmitter()), false, "UTF-8");
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
        if (JenkinsJVM.isJenkinsJVM()) { // TODO why, it is possible because in the Agent closing of channel kill all the resources but on the Jenkins controller not.
            OtelLogStorageFactory.get().close(buildInfo);
        }
    }

    abstract LogEmitter getLogEmitter();

    /**
     * {@link OtelLogSenderBuildListener} implementation that runs on the Jenkins Controller and
     * that retrieves the {@link LogEmitter} from the {@link OpenTelemetrySdkProvider}
     */
    static final class OtelLogSenderBuildListenerOnController extends OtelLogSenderBuildListener {
        private static final long serialVersionUID = 1;

        transient LogEmitter logEmitter;

        public OtelLogSenderBuildListenerOnController(@Nonnull BuildInfo buildInfo) {
            super(buildInfo);
        }

        public OtelLogSenderBuildListenerOnController(@Nonnull BuildInfo buildInfo, @Nonnull FlowNode node) {
            super(buildInfo, node);
        }

        @Override
        public LogEmitter getLogEmitter() {
            if (logEmitter == null) {
                logEmitter = OpenTelemetrySdkProvider.get().getLogEmitter();
            }
            return logEmitter;
        }

        private Object writeReplace() throws IOException {
            return new OtelLogSenderBuildListenerOnAgent(buildInfo);
        }
    }

    /**
     * {@link OtelLogSenderBuildListener} implementation that runs on the Jenkins Agents and
     * that retrieves the {@link LogEmitter} instantiating an {@link io.opentelemetry.sdk.OpenTelemetrySdk} with
     * configuration parameters transmitted via Jenkins remoting serialization
     */
    public static class OtelLogSenderBuildListenerOnAgent extends OtelLogSenderBuildListener {
        private static final long serialVersionUID = 1;

        private final static Logger LOGGER = Logger.getLogger(OtelLogSenderBuildListenerOnAgent.class.getName());
        transient LogEmitter logEmitter;

        public OtelLogSenderBuildListenerOnAgent(@Nonnull BuildInfo buildInfo) {
            super(buildInfo);
        }

        @Override
        public LogEmitter getLogEmitter() {
            // will return a noop LogEmitter
            return SdkLogEmitterProvider.builder().build().get("jenkins-on-agent");
        }
    }
}
