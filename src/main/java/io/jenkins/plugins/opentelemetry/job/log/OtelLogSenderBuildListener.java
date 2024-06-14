/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.BuildListener;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.opentelemetry.GlobalOpenTelemetrySdk;
import io.jenkins.plugins.opentelemetry.opentelemetry.common.Clocks;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.sdk.common.Clock;
import jenkins.util.JenkinsJVM;
import org.jenkinsci.plugins.workflow.log.OutputStreamTaskListener;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
abstract class OtelLogSenderBuildListener implements BuildListener, OutputStreamTaskListener {

    protected final static Logger LOGGER = Logger.getLogger(OtelLogSenderBuildListener.class.getName());
    final RunTraceContext runTraceContext;

    final Map<String, String> otelConfigProperties;
    final Map<String, String> otelResourceAttributes;
    /**
     * Timestamps of the logs emitted by the Jenkins Agents must be chronologically ordered with the timestamps of
     * the logs & traces emitted on the Jenkins controller even if the system clock are not perfectly synchronized
     */
    @SuppressFBWarnings("IS2_INCONSISTENT_SYNC")
    transient Clock clock;

    @CheckForNull
    transient OutputStream outputStream;

    @CheckForNull
    transient PrintStream logger;

    public OtelLogSenderBuildListener(@NonNull RunTraceContext runTraceContext, @NonNull Map<String, String> otelConfigProperties, @NonNull Map<String, String> otelResourceAttributes) {
        this.runTraceContext = runTraceContext;
        this.otelConfigProperties = otelConfigProperties;
        this.otelResourceAttributes = otelResourceAttributes;
        this.clock = Clocks.monotonicClock();
        // Constructor must always be invoked on the Jenkins Controller.
        // Instantiation on the Jenkins Agents is done via deserialization.
        JenkinsJVM.checkJenkinsJVM();
    }

    @NonNull
    @Override
    public synchronized final OutputStream getOutputStream() {
        if (outputStream == null) {
            outputStream = new OtelLogOutputStream(runTraceContext, getOtelLogger(), clock);
        }
        return outputStream;
    }

    @NonNull
    @Override
    public synchronized final PrintStream getLogger() {
        if (logger == null) {
            logger = new PrintStream(new OtelLogOutputStream(runTraceContext, getOtelLogger(), clock), false, StandardCharsets.UTF_8);
        }
        return logger;
    }

    abstract io.opentelemetry.api.logs.Logger getOtelLogger();

    /**
     * {@link OtelLogSenderBuildListener} implementation that runs on the Jenkins Controller and
     * that retrieves the {@link io.opentelemetry.api.logs.Logger} from the {@link OpenTelemetrySdkProvider}
     */
    static final class OtelLogSenderBuildListenerOnController extends OtelLogSenderBuildListener {
        private static final long serialVersionUID = 1;

        private final static Logger logger = Logger.getLogger(OtelLogSenderBuildListenerOnController.class.getName());

        public OtelLogSenderBuildListenerOnController(@NonNull RunTraceContext runTraceContext, @NonNull Map<String, String> otelConfigProperties, @NonNull Map<String, String> otelResourceAttributes) {
            super(runTraceContext, otelConfigProperties, otelResourceAttributes);
            logger.log(Level.FINEST, () -> "new OtelLogSenderBuildListenerOnController()");
            JenkinsJVM.checkJenkinsJVM();
        }

        @Override
        public io.opentelemetry.api.logs.Logger getOtelLogger() {
            JenkinsJVM.checkJenkinsJVM();
            return OpenTelemetrySdkProvider.get().getLoggerProvider().get(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME);
        }

        /**
         * Java serialization to send the {@link OtelLogSenderBuildListener} from the Jenkins Controller to a Jenkins Agent.
         * Swap the instance from a {@link OtelLogSenderBuildListenerOnController} to a {@link OtelLogSenderBuildListenerOnAgent}
         * to change the implementation of {@link #getOtelLogger()}.
         *
         * See https://docs.oracle.com/en/java/javase/11/docs/specs/serialization/output.html#the-writereplace-method
         */
        private Object writeReplace() throws IOException {
            logger.log(Level.FINEST, () -> "writeReplace()");
            JenkinsJVM.checkJenkinsJVM();
            return new OtelLogSenderBuildListenerOnAgent(runTraceContext, otelConfigProperties, otelResourceAttributes);
        }
    }

    /**
     * {@link OtelLogSenderBuildListener} implementation that runs on the Jenkins Agents and
     * that retrieves the {@link io.opentelemetry.api.logs.Logger} instantiating an
     * {@link io.opentelemetry.sdk.OpenTelemetrySdk} with configuration parameters transmitted via Jenkins remoting
     * serialization
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
        private OtelLogSenderBuildListenerOnAgent(@NonNull RunTraceContext runTraceContext, @NonNull Map<String, String> otelConfigProperties, @NonNull Map<String, String> otelResourceAttributes) {
            super(runTraceContext, otelConfigProperties, otelResourceAttributes);
            logger.log(Level.FINEST, () -> "new OtelLogSenderBuildListenerOnAgent()");
            JenkinsJVM.checkJenkinsJVM();
        }

        /**
         *
         * @return
         */
        @Override
        public io.opentelemetry.api.logs.Logger getOtelLogger() {
            JenkinsJVM.checkNotJenkinsJVM();
            return GlobalOpenTelemetrySdk.getOtelLogger();
        }

        private void writeObject(ObjectOutputStream stream) throws IOException {
            logger.log(Level.FINEST, () -> "writeObject(): set instantInNanosOnJenkinsControllerBeforeSerialization");
            JenkinsJVM.checkJenkinsJVM();
            this.instantInNanosOnJenkinsControllerBeforeSerialization = Clock.getDefault().now();
            stream.defaultWriteObject();
        }


        private Object readResolve() {
            JenkinsJVM.checkNotJenkinsJVM();

            /*
             * Timestamps of the logs emitted by the Jenkins Agents must be chronologically ordered with the timestamps of
             * the logs & traces emitted on the Jenkins controller even if the system clock are not perfectly synchronized
             */
            if (instantInNanosOnJenkinsControllerBeforeSerialization == 0) {
                logger.log(Level.INFO, () -> "adjustClock: unexpected timeBeforeSerialization of 0ns, don't adjust the clock");
                this.clock = Clocks.monotonicClock();
            } else {
                long instantInNanosOnJenkinsAgentAtDeserialization = Clock.getDefault().now();
                long offsetInNanosOnJenkinsAgent = instantInNanosOnJenkinsControllerBeforeSerialization - instantInNanosOnJenkinsAgentAtDeserialization;
                logger.log(Level.FINE, () ->
                    "adjustClock: " +
                        "offsetInNanos: " + TimeUnit.MILLISECONDS.convert(offsetInNanosOnJenkinsAgent, TimeUnit.NANOSECONDS) + "ms / " + offsetInNanosOnJenkinsAgent + "ns. "+
                        "A negative offset of few milliseconds is expected due to the latency of the communication from the Jenkins Controller to the Jenkins Agent. " +
                        "Higher offsets indicate a synchronization gap of the system clocks between the Jenkins Controller that will be work arounded by the clock adjustment."
                );
                this.clock = Clocks.monotonicOffsetClock(offsetInNanosOnJenkinsAgent);
            }

            // Setup OTel
            GlobalOpenTelemetrySdk.configure(
                otelConfigProperties,
                otelResourceAttributes,
                /* the JVM shutdown hook is too late to flush the Otel signals as the OTel classes have been unloaded */
                false );
            // TODO find the right lifecycle event to shutdown the Otel SDK on agent shutdown
            // hudson.remoting.EngineListener doesn't seem to be the right event
            return this;
        }


    }
}
