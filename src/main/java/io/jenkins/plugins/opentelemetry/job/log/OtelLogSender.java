/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.console.LineTransformationOutputStream;
import hudson.console.PlainTextConsoleOutputStream;
import hudson.model.BuildListener;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.logs.LogEmitter;
import jenkins.util.JenkinsJVM;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java
 */
class OtelLogSender implements BuildListener, Closeable {
    protected final Logger LOGGER = Logger.getLogger(getClass().getName());
    @CheckForNull
    transient PrintStream logger;

    final BuildInfo buildInfo;

    final Map<String, String> context;

    public OtelLogSender(BuildInfo buildInfo, Map<String, String> context) {
        this.buildInfo = buildInfo;
        this.context = context;
    }

    @Nonnull
    @Override
    public synchronized final PrintStream getLogger() {
        if (logger == null) {
            try {
                logger = new PrintStream(new PlainTextConsoleOutputStream(new OtelLogOutputStream(buildInfo, context)), false, "UTF-8");
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


    /**
     * TODO support Pipeline Step Context {@link Context} in addition to supporting run root context
     * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java#L162
     */
    private class OtelLogOutputStream extends LineTransformationOutputStream {
        final BuildInfo buildInfo;
        final Map<String, String> contextAsMap;
        transient LogEmitter logEmitter;
        transient Context context;

        /**
         * @param buildInfo
         * @param context   see {@link Context} and
         */
        public OtelLogOutputStream(BuildInfo buildInfo, Map<String, String> context) {
            this.buildInfo = buildInfo;
            this.contextAsMap = context;
        }

        LogEmitter getLogEmitter() {
            if (logEmitter == null) {
                logEmitter = OpenTelemetrySdkProvider.get().getLogEmitter();
            }
            return logEmitter;
        }

        Context getContext() {
            if (context == null) {
                context = W3CTraceContextPropagator.getInstance().extract(Context.current(), this.contextAsMap, new TextMapGetter<Map<String, String>>() {
                    @Override
                    public Iterable<String> keys(Map<String, String> carrier) {
                        return carrier.keySet();
                    }

                    @Nullable
                    @Override
                    public String get(@Nullable Map<String, String> carrier, String key) {
                        return carrier == null ? null : carrier.get(key);
                    }
                });
            }
            return context;
        }

        @Override
        protected void eol(byte[] bytes, int len) throws IOException {
            if (len == 0) {
                return;
            }
            String message = new String(bytes, 0, len - 1, StandardCharsets.UTF_8); //remove trailing line feed

            getLogEmitter().logBuilder()
                .setAttributes(buildInfo.toAttributes())
                .setBody(message)
                .setContext(getContext())
                .emit();
            LOGGER.log(Level.FINE, () -> buildInfo + " - emit '" + message + "'"); // FIXME change log level
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
