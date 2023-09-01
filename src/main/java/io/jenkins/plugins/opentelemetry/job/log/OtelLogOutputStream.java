package io.jenkins.plugins.opentelemetry.job.log;

import hudson.console.LineTransformationOutputStream;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.common.Clock;
import org.apache.commons.lang.StringUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Process the output stream and send it to OpenTelemetry.
 * TODO support Pipeline Step Context {@link Context} in addition to supporting run root context.
 * TODO should we implement a MonotonicallyIncreasedClock to ensure the logs messages are always well sorted? Will backends truncate nano seconds to just do millis and loose this monotonic nature ?
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java#L162
 */
final class OtelLogOutputStream extends LineTransformationOutputStream {
    public static boolean ENABLE_LOG_FORMATTING = Boolean.parseBoolean(System.getProperty("pipeline.log.elastic.enable.log.formatting", "false"));
    private final static Logger LOGGER = Logger.getLogger(OtelLogOutputStream.class.getName());

    @NonNull
    final BuildInfo buildInfo;
    @Nullable String flowNodeId;
    /**
     * {@link Map} version of the {@link Context} used to associate log message with the right {@link Span}
     */
    final Map<String, String> w3cTraceContext;
    final io.opentelemetry.api.logs.Logger otelLogger;
    final Context context;
    final Clock clock;

    /**
     * @param w3cTraceContext Serializable version of the {@link Context} used to associate log messages with {@link io.opentelemetry.api.trace.Span}s
     */
    public OtelLogOutputStream(@NonNull BuildInfo buildInfo, @Nullable String flowNodeId, @NonNull Map<String, String> w3cTraceContext, @NonNull io.opentelemetry.api.logs.Logger otelLogger, @NonNull Clock clock) {
        this.buildInfo = buildInfo;
        this.flowNodeId = flowNodeId;
        this.otelLogger = otelLogger;
        this.clock = clock;
        this.w3cTraceContext = w3cTraceContext;
        this.context = W3CTraceContextPropagator.getInstance().extract(Context.current(), this.w3cTraceContext, new TextMapGetter<>() {
            @Override
            public Iterable<String> keys(@Nonnull Map<String, String> carrier) {
                return carrier.keySet();
            }

            @Nullable
            @Override
            public String get(@Nullable Map<String, String> carrier, @Nonnull String key) {
                return carrier == null ? null : carrier.get(key);
            }
        });
    }

    @Override
    protected void eol(byte[] bytes, int len) {
        if (len == 0) {
            return;
        }
        ConsoleNotes.TextAndAnnotations textAndAnnotations = ConsoleNotes.parse(bytes, len);
        String plainLogLine = textAndAnnotations.text;
        if (plainLogLine == null || plainLogLine.isEmpty()) {
            LOGGER.log(Level.FINEST, () -> buildInfo + " - skip empty log line");
        } else {
            AttributesBuilder attributesBuilder = Attributes.builder();
            if (ENABLE_LOG_FORMATTING && textAndAnnotations.annotations != null) {
                attributesBuilder.put(JenkinsOtelSemanticAttributes.JENKINS_ANSI_ANNOTATIONS, textAndAnnotations.annotations.toString());
            }
            attributesBuilder.putAll(buildInfo.toAttributes());
            if (flowNodeId != null) {
                attributesBuilder.put(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, flowNodeId);
            }
            otelLogger.logRecordBuilder()
                .setSeverity(Severity.INFO)
                .setBody(plainLogLine)
                .setAllAttributes(attributesBuilder.build())
                .setContext(context)
                .setTimestamp(clock.now(), TimeUnit.NANOSECONDS)
                .emit();
            LOGGER.log(Level.FINEST, () -> buildInfo.jobFullName + "#" + buildInfo.runNumber + " - emit body: '" + StringUtils.abbreviate(plainLogLine, 30) + "'");
        }
    }

    @Override
    public void flush() {
        // there is no flush concept with the Otel Logger
    }

    @Override
    public void close() {
        // TODO anything to do? cyrille: probably not
    }
}