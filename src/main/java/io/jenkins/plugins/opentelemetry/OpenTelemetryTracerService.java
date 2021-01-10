package io.jenkins.plugins.opentelemetry;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceAttributes;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import javax.annotation.concurrent.Immutable;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class OpenTelemetryTracerService {

    private static Logger LOGGER = Logger.getLogger(OpenTelemetryTracerService.class.getName());

    private final Multimap<RunIdentifier, Span> spansByRun = ArrayListMultimap.create();

    private final Multimap<RunIdentifier, SpanIdentifier> spanIdentifiersByRun = ArrayListMultimap.create();

    private transient OpenTelemetrySdk openTelemetry;

    private transient Tracer tracer;

    public OpenTelemetryTracerService() {
        initialize();
    }

    protected Object readResolve() {
        initialize();
        return this;
    }

    public void initialize() {
        Resource resource =  Resource.getDefault().merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "jenkins")));

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().setResource(resource).build();

        OtlpGrpcSpanExporter otlpGrpcSpanExporter = OtlpGrpcSpanExporter.builder().setEndpoint("localhost:4317").setUseTls(false).build();
        LoggingSpanExporter loggingSpanExporter = new LoggingSpanExporter();
        SpanProcessor spanProcessor = SpanProcessor.composite(
                SimpleSpanProcessor.builder(loggingSpanExporter).build(),
                SimpleSpanProcessor.builder(otlpGrpcSpanExporter).build());

        sdkTracerProvider.addSpanProcessor(spanProcessor);

        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GlobalOpenTelemetry.set(openTelemetry);

        this.tracer = openTelemetry.getTracer("jenkins");
        LOGGER.log(Level.INFO, "OpenTelemetry initialized");
    }

    @PreDestroy
    public void preDestroy() {
        openTelemetry.getTracerManagement().shutdown();
    }

    @CheckForNull
    public Span getSpan(@NonNull Run run) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        LOGGER.log(Level.INFO, "Run {0}, get span. Stack: {1}", new Object[]{run, this.spanIdentifiersByRun.asMap().get(runIdentifier)});
        Collection<Span> runSpans = this.spansByRun.get(runIdentifier);
        return runSpans == null ? null : Iterables.getLast(runSpans, null);
    }

    public boolean removeSpan(@Nonnull Run run, @Nonnull Span span) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        SpanIdentifier spanIdentifier = SpanIdentifier.fromSpanData(((ReadableSpan) span).toSpanData());
        boolean spanRemoved = this.spansByRun.remove(runIdentifier, span);
        boolean spanIdentifierRemoved = this.spanIdentifiersByRun.remove(runIdentifier, spanIdentifier);
        LOGGER.log(Level.INFO, "Run " + run + ", remove span " + span + ", new stack: " + spanIdentifiersByRun.asMap().get(run));
        if (spanRemoved && spanIdentifierRemoved) {
            return true;
        } else if (!spanIdentifierRemoved && !spanRemoved) {
            return false;
        } else {
            LOGGER.log(Level.INFO, "Problem removing span " + span + " on run " + run + ": spanIdentifierRemoved: " + spanIdentifierRemoved + ", spanRemoved: " + spanRemoved);
            return true;
        }
    }

    /**
     * @param run
     * @return count of remaining spans found for the given run {@link Run}
     */
    public int purgeRun(@Nonnull Run run) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        Collection<SpanIdentifier> remainingSpanIdentifiers = this.spanIdentifiersByRun.removeAll(runIdentifier);
        Collection<Span> remainingSpans = this.spansByRun.removeAll(runIdentifier);
        LOGGER.log(Level.INFO, "Run " + run + ", purge spans, new stack: " + spanIdentifiersByRun.asMap().get(run));

        // TODO shall we {@code Span#end()} all these spans?
        return remainingSpanIdentifiers.size();
    }

    public void dumpContext(@Nonnull Run run, String message, @Nonnull PrintStream out) {
        ReadableSpan span = (ReadableSpan) getSpan(run);
        SpanData spanData = span.toSpanData();
        out.println("OPENTELEMETRY: " + message + " - traceId: " + spanData.getTraceId() + ", spanId: " + spanData.getSpanId() + ", name: " + spanData.getName());
    }


    public void putSpan(@NonNull Run run, @NonNull Span span) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        SpanIdentifier spanIdentifier = SpanIdentifier.fromSpanData(((ReadableSpan) span).toSpanData());
        this.spansByRun.put(runIdentifier, span);
        this.spanIdentifiersByRun.put(runIdentifier, spanIdentifier);

        LOGGER.log(Level.INFO, "Run " + run + ", put span " + span + ", new stack: " + spanIdentifiersByRun.asMap().get(run));

    }

    public OpenTelemetrySdk getOpenTelemetry() {
        return openTelemetry;
    }

    public Tracer getTracer() {
        return tracer;
    }

    @Immutable
    public static class RunIdentifier implements Serializable, Comparable<RunIdentifier> {
        final String jobName;
        final int runNumber;

        static RunIdentifier fromRun(@NonNull Run run) {
            return new RunIdentifier(run.getParent().getFullName(), run.getNumber());
        }

        public RunIdentifier(@NonNull String jobName, @NonNull int runNumber) {
            this.jobName = jobName;
            this.runNumber = runNumber;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RunIdentifier that = (RunIdentifier) o;
            return runNumber == that.runNumber && jobName.equals(that.jobName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jobName, runNumber);
        }

        @Override
        public String toString() {
            return "RunIdentifier{" +
                    "jobName='" + jobName + '\'' +
                    ", runNumber=" + runNumber +
                    '}';
        }

        public String getJobName() {
            return jobName;
        }

        public int getRunNumber() {
            return runNumber;
        }

        @Override
        public int compareTo(RunIdentifier o) {
            return ComparisonChain.start().compare(this.jobName, o.jobName).compare(this.runNumber, o.runNumber).result();
        }
    }

    @Immutable
    public static class SpanIdentifier implements Comparable<SpanIdentifier> {
        final String traceId;
        final String spanId;
        final String parentSpanId;
        final String name;
        final long startEpochNanos;

        public static SpanIdentifier fromSpanData(@Nonnull SpanData spanData) {
            return new SpanIdentifier(spanData.getTraceId(), spanData.getSpanId(), spanData.getParentSpanId(),
                    spanData.getName(), spanData.getStartEpochNanos());
        }

        public SpanIdentifier(@Nonnull String traceId, @Nonnull String spanId, @Nonnull String parentSpanId, @Nonnull String name, @Nonnull long startEpochNanos) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.name = name;
            this.startEpochNanos = startEpochNanos;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SpanIdentifier that = (SpanIdentifier) o;
            return spanId.equals(that.spanId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(spanId);
        }

        @Override
        public String toString() {
            return "SpanIdentifier{" +
                    "traceId='" + traceId + '\'' +
                    ", spanId='" + spanId + '\'' +
                    ", parentSpanId='" + parentSpanId + '\'' +
                    ", name='" + name + '\'' +
                    ", startEpochNanos=" + startEpochNanos +
                    '}';
        }

        @Override
        public int compareTo(SpanIdentifier o) {
            return Long.compare(this.startEpochNanos, o.startEpochNanos);
        }

        public String getTraceId() {
            return traceId;
        }

        public String getSpanId() {
            return spanId;
        }

        public String getParentSpanId() {
            return parentSpanId;
        }

        public String getName() {
            return name;
        }

        public long getStartEpochNanos() {
            return startEpochNanos;
        }
    }
}
