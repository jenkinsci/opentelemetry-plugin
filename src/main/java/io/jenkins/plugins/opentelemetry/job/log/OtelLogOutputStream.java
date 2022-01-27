package io.jenkins.plugins.opentelemetry.job.log;

import hudson.console.LineTransformationOutputStream;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.logs.LogEmitter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
    final Map<String, String> contextAsMap;
    private final Logger LOGGER = Logger.getLogger(OtelLogOutputStream.class.getName());
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

    private LogEmitter getLogEmitter() {
        if (logEmitter == null) {
            logEmitter = OpenTelemetrySdkProvider.get().getLogEmitter();
        }
        return logEmitter;
    }

    private Context getContext() {
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