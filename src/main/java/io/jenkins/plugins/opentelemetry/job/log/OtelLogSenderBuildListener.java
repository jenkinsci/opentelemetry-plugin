/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.model.BuildListener;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.opentelemetry.GlobalOpenTelemetrySdk;
import io.jenkins.plugins.opentelemetry.opentelemetry.common.OffsetClock;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.logs.LogEmitter;
import jenkins.util.JenkinsJVM;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

    protected final static Logger LOGGER = Logger.getLogger(OtelLogSenderBuildListener.class.getName());
    final BuildInfo buildInfo;
    final Map<String, String> w3cTraceContext;
    final Map<String, String> otelConfigProperties;
    final Map<String, String> otelResourceAttributes;
    /**
     * Timestamps of the logs emitted by the Jenkins Agents must be chronologically ordered with the timestamps of
     * the logs & traces emitted on the Jenkins controller even if the system clock are not perfectly synchronized
     */
    transient Clock clock;

    @CheckForNull
    transient PrintStream logger;

    public OtelLogSenderBuildListener(@Nonnull BuildInfo buildInfo, @Nonnull Map<String, String> otelConfigProperties, @Nonnull Map<String, String> otelResourceAttributes) {
        this.buildInfo = new BuildInfo(buildInfo);
        this.w3cTraceContext = buildInfo.getW3cTraceContext();
        this.otelConfigProperties = otelConfigProperties;
        this.otelResourceAttributes = otelResourceAttributes;
        this.clock = Clock.getDefault();
        // Constructor must always be invoked on the Jenkins Controller.
        // Instantiation on the Jenkins Agents is done via deserialization.
        JenkinsJVM.checkJenkinsJVM();
    }

    @Nonnull
    @Override
    public synchronized final PrintStream getLogger() {
        if (logger == null) {
            try {
                logger = new PrintStream(new OtelLogOutputStream(buildInfo, w3cTraceContext, getLogEmitter(), clock), false, "UTF-8");
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

        private final static Logger logger = Logger.getLogger(OtelLogSenderBuildListenerOnController.class.getName());

        public OtelLogSenderBuildListenerOnController(@Nonnull BuildInfo buildInfo, @Nonnull Map<String, String> otelConfigProperties, @Nonnull Map<String, String> otelResourceAttributes) {
            super(buildInfo, otelConfigProperties, otelResourceAttributes);
            logger.log(Level.FINEST, () -> "new OtelLogSenderBuildListenerOnController()");
            JenkinsJVM.checkJenkinsJVM();
        }

        @Override
        public LogEmitter getLogEmitter() {
            JenkinsJVM.checkJenkinsJVM();
            return OpenTelemetrySdkProvider.get().getLogEmitter();
        }

        /**
         * Java serialization to send the {@link OtelLogSenderBuildListener} from the Jenkins Controller to a Jenkins Agent.
         * Swap the instance from a {@link OtelLogSenderBuildListenerOnController} to a {@link OtelLogSenderBuildListenerOnAgent}
         * to change the implementation of {@link #getLogEmitter()}.
         *
         * See https://docs.oracle.com/en/java/javase/11/docs/specs/serialization/output.html#the-writereplace-method
         */
        private Object writeReplace() throws IOException {
            logger.log(Level.FINEST, () -> "writeReplace()");
            JenkinsJVM.checkJenkinsJVM();
            return new OtelLogSenderBuildListenerOnAgent(buildInfo, otelConfigProperties, otelResourceAttributes);
        }
    }

    /**
     * {@link OtelLogSenderBuildListener} implementation that runs on the Jenkins Agents and
     * that retrieves the {@link LogEmitter} instantiating an {@link io.opentelemetry.sdk.OpenTelemetrySdk} with
     * configuration parameters transmitted via Jenkins remoting serialization
     */
    private static class OtelLogSenderBuildListenerOnAgent extends OtelLogSenderBuildListener {
        private static final long serialVersionUID = 1;

        private final static Logger logger = Logger.getLogger(OtelLogSenderBuildListenerOnAgent.class.getName());

        /**
         * Used to determine the clock adjustment on the Jenkins Agent.
         */
        private long instantInNanosOnJenkinsControllerBeforeSerialization;

        /**
         * Intended to be exclusively called on the Jenkins Controller by {@link OtelLogSenderBuildListenerOnController#writeReplace()}.
         */
        private OtelLogSenderBuildListenerOnAgent(@Nonnull BuildInfo buildInfo, @Nonnull Map<String, String> otelConfigProperties, @Nonnull Map<String, String> otelResourceAttributes) {
            super(buildInfo, otelConfigProperties, otelResourceAttributes);
            logger.log(Level.FINEST, () -> "new OtelLogSenderBuildListenerOnAgent()");
            JenkinsJVM.checkJenkinsJVM();
        }

        /**
         *
         * @return
         */
        @Override
        public LogEmitter getLogEmitter() {
            JenkinsJVM.checkNotJenkinsJVM();
            return GlobalOpenTelemetrySdk.getLogEmitter();
        }

        private void writeObject(ObjectOutputStream stream) throws IOException {
            logger.log(Level.FINEST, () -> "writeObject(): set instantInNanosOnJenkinsControllerBeforeSerialization");
            JenkinsJVM.checkJenkinsJVM();
            this.instantInNanosOnJenkinsControllerBeforeSerialization = Clock.getDefault().now();
            stream.defaultWriteObject();
        }


        private Object readResolve() {
            adjustClock();
            GlobalOpenTelemetrySdk.configure(
                otelConfigProperties,
                otelResourceAttributes,
                true /* register shutdown hook when on the Jenkins agents */);
            return this;
        }

        /**
         * Timestamps of the logs emitted by the Jenkins Agents must be chronologically ordered with the timestamps of
         * the logs & traces emitted on the Jenkins controller even if the system clock are not perfectly synchronized
         */
        private void adjustClock() {
            JenkinsJVM.checkNotJenkinsJVM();
            if (instantInNanosOnJenkinsControllerBeforeSerialization == 0) {
                logger.log(Level.INFO, () -> "adjustClock(): unexpected timeBeforeSerialization of 0ns, don't adjust the clock");
                this.clock = Clock.getDefault();
            } else {
                long instantInNanosOnJenkinsAgentAtDeserialization = Clock.getDefault().now();
                long offsetInNanosOnJenkinsAgent = instantInNanosOnJenkinsControllerBeforeSerialization - instantInNanosOnJenkinsAgentAtDeserialization;
                logger.log(Level.INFO, () ->
                    "adjustClock(): " +
                        "offsetInNanos: " + TimeUnit.MILLISECONDS.convert(offsetInNanosOnJenkinsAgent, TimeUnit.NANOSECONDS) + "ms / " + offsetInNanosOnJenkinsAgent + "ns. "+
                        "A negative offset of few milliseconds is expected due to the latency of the communication from the Jenkins Controller to the Jenkins Agent. " +
                        "Higher offsets indicate a synchronization gap of the system clocks between the Jenkins Controller that will be work arounded by the clock adjustment."
                );
                this.clock = OffsetClock.offsetClock(offsetInNanosOnJenkinsAgent);
            }
        }
    }
}
