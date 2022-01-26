package io.jenkins.plugins.opentelemetry.job.log;

import hudson.console.LineTransformationOutputStream;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogEmitter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Process the output stream and send it to OpenTelemetry.
 * TODO support Pipeline Step Context {@link Context} in addition to supporting run root context
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java#L162
 */
public class OtelLogOutputStream extends LineTransformationOutputStream {
    @Nonnull
    final BuildInfo buildInfo;
    private final Logger LOGGER = Logger.getLogger(getClass().getName());
    transient LogEmitter logEmitter;
    transient Context context;

    public OtelLogOutputStream(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    private LogEmitter getLogEmitter() {
        if (logEmitter == null) {
            logEmitter = OpenTelemetrySdkProvider.get().getLogEmitter();
        }
        return logEmitter;
    }

    private Context getContext() {
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
        String message = new String(bytes, 0, len - 1, StandardCharsets.UTF_8); //remove trailing line feed
        getLogEmitter().logBuilder()
            .setAttributes(Attributes.builder().putAll(ConsoleNotes.parse(bytes, len)).putAll(buildInfo.toAttributes()).build())
            .setBody(message)
            .setContext(getContext())
            .emit();
        LOGGER.log(Level.FINE, () -> buildInfo + " - emit '" + message + "'");
    }

    @Override
    public void flush() throws IOException {
        // there is no flush concept with the Otel LogEmitter
    }

    @Override
    public void close() throws IOException {
    }
}