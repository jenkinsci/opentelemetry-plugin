package io.jenkins.plugins.opentelemetry.job.log;

import hudson.console.LineTransformationOutputStream;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.logs.LogEmitter;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Process the output stream and send it to OpenTelemetry.
 * TODO support Pipeline Step Context {@link Context} in addition to supporting run root context
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java#L162
 */
public class OtelLogOutputStream extends LineTransformationOutputStream {
    public static boolean ENABLE_LOG_FORMATTING = Boolean.valueOf(System.getProperty("pipeline.log.elastic.enable.log.formatting", "false"));

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
        ConsoleNotes.TextAndAnnotations textAndAnnotations = ConsoleNotes.parse(bytes, len);
        String plainLogLine = textAndAnnotations.text;
        if (plainLogLine == null || plainLogLine.isEmpty()) {
            LOGGER.log(Level.FINER, () -> buildInfo + " - skip empty log line");
        } else {
            AttributesBuilder attributesBuilder = Attributes.builder();
            if (ENABLE_LOG_FORMATTING && textAndAnnotations.annotations != null) {
                attributesBuilder.put(JenkinsOtelSemanticAttributes.JENKINS_ANSI_ANNOTATIONS, textAndAnnotations.annotations.toString());
            }
            attributesBuilder.putAll(buildInfo.toAttributes());
            getLogEmitter().logBuilder()
                .setAttributes(attributesBuilder.build())
                .setBody(plainLogLine)
                .setContext(getContext())
                .emit();
            LOGGER.log(Level.INFO, () -> buildInfo.jobFullName + "#" + buildInfo.runNumber + " - emit body: '" + StringUtils.abbreviate(plainLogLine, 30) + "'");
        }
    }

    @Override
    public void flush() throws IOException {
        // there is no flush concept with the Otel LogEmitter
    }

    @Override
    public void close() throws IOException {
    }
}