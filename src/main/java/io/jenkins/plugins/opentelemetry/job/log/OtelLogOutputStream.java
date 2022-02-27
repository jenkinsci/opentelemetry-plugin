package io.jenkins.plugins.opentelemetry.job.log;

import hudson.console.LineTransformationOutputStream;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.logs.LogEmitter;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Process the output stream and send it to OpenTelemetry.
 * TODO support Pipeline Step Context {@link Context} in addition to supporting run root context.
 * TODO should we implement a MonotonicallyIncreasedClock to ensure the logs messages are always well sorted? Will backends truncate nano seconds to just do millis and loose this monotonical nature ?
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java#L162
 */
final class OtelLogOutputStream extends LineTransformationOutputStream {
    public static boolean ENABLE_LOG_FORMATTING = Boolean.valueOf(System.getProperty("pipeline.log.elastic.enable.log.formatting", "false"));
    private final static Logger LOGGER = Logger.getLogger(OtelLogOutputStream.class.getName());

    @Nonnull
    final BuildInfo buildInfo;
    @Nullable String flowNodeId;
    /**
     * {@link Map} version of the {@link Context} used to associate log message with the right {@link Span}
     */
    final Map<String, String> w3cTraceContext;
    final LogEmitter logEmitter;
    final Context context;
    final Clock clock;

    /**
     * @param buildInfo
     * @param w3cTraceContext Serializable version of the {@link Context} used to associate log messages with {@link io.opentelemetry.api.trace.Span}s
     */
    public OtelLogOutputStream(@Nonnull BuildInfo buildInfo, @Nullable String flowNodeId, @Nonnull Map<String, String> w3cTraceContext, @Nonnull LogEmitter logEmitter, @Nonnull Clock clock) {
        this.buildInfo = buildInfo;
        this.flowNodeId = flowNodeId;
        this.logEmitter = logEmitter;
        this.clock = clock;
        this.w3cTraceContext = w3cTraceContext;
        this.context = W3CTraceContextPropagator.getInstance().extract(Context.current(), this.w3cTraceContext, new TextMapGetter<Map<String, String>>() {
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
            if (flowNodeId != null) {
                attributesBuilder.put(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, flowNodeId);
            }
            logEmitter.logBuilder()
                .setBody(plainLogLine)
                .setAttributes(attributesBuilder.build())
                .setContext(context)
                .setEpoch(clock.now(), TimeUnit.NANOSECONDS)
                .emit();
            LOGGER.log(Level.FINER, () -> buildInfo.jobFullName + "#" + buildInfo.runNumber + " - emit body: '" + StringUtils.abbreviate(plainLogLine, 30) + "'");
        }
    }

    @Override
    public void flush() throws IOException {
        // there is no flush concept with the Otel LogEmitter
    }

    @Override
    public void close() throws IOException {
        // TODO anything to do? cyrille: probably not
    }
}