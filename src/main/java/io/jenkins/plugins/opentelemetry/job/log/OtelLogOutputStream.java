package io.jenkins.plugins.opentelemetry.job.log;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.console.LineTransformationOutputStream;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.Clock;
import org.apache.commons.lang.StringUtils;

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
    final RunTraceContext runTraceContext;

    final io.opentelemetry.api.logs.Logger otelLogger;
    final Clock clock;

    public OtelLogOutputStream(@NonNull RunTraceContext runTraceContext, @NonNull io.opentelemetry.api.logs.Logger otelLogger, @NonNull Clock clock) {
        this.runTraceContext = runTraceContext;
        this.otelLogger = otelLogger;
        this.clock = clock;
    }

    @Override
    protected void eol(byte[] bytes, int len) {
        if (len == 0) {
            return;
        }
        ConsoleNotes.TextAndAnnotations textAndAnnotations = ConsoleNotes.parse(bytes, len);
        String plainLogLine = textAndAnnotations.text;
        if (plainLogLine == null || plainLogLine.isEmpty()) {
            LOGGER.log(Level.FINEST, () -> runTraceContext + " - skip empty log line");
        } else {
            AttributesBuilder attributesBuilder = Attributes.builder();
            if (ENABLE_LOG_FORMATTING && textAndAnnotations.annotations != null) {
                attributesBuilder.put(ExtendedJenkinsAttributes.JENKINS_ANSI_ANNOTATIONS, textAndAnnotations.annotations.toString());
            }
            attributesBuilder.putAll(runTraceContext.toAttributes());

            otelLogger.logRecordBuilder()
                .setSeverity(Severity.INFO)
                .setBody(plainLogLine)
                .setAllAttributes(attributesBuilder.build())
                .setContext(runTraceContext.getContext())
                .setTimestamp(clock.now(), TimeUnit.NANOSECONDS)
                .emit();
            LOGGER.log(Level.FINEST, () -> runTraceContext.jobFullName + "#" + runTraceContext.runNumber + " - emit body: '" + StringUtils.abbreviate(plainLogLine, 30) + "'");
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