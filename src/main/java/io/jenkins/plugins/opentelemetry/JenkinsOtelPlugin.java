package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.GlobalMetricsProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class JenkinsOtelPlugin {
    @SuppressFBWarnings
    protected static boolean TESTING_INMEMORY_MODE = false;
    @SuppressFBWarnings
    protected static MetricExporter TESTING_METRICS_EXPORTER ;
    @SuppressFBWarnings
    protected static SpanExporter TESTING_SPAN_EXPORTER;

    private static Logger LOGGER = Logger.getLogger(JenkinsOtelPlugin.class.getName());

    protected transient OpenTelemetrySdk openTelemetry;

    protected transient Tracer tracer;

    protected transient Meter meter;

    protected transient IntervalMetricReader intervalMetricReader;

    public JenkinsOtelPlugin() {
        initialize();
    }

    protected Object readResolve() {
        initialize();
        return this;
    }

    public void initialize() {
        if (TESTING_INMEMORY_MODE) {
            // METRICS
            // See https://github.com/open-telemetry/opentelemetry-java/blob/v0.14.1/examples/otlp/src/main/java/io/opentelemetry/example/otlp/OtlpExporterExample.java
            SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder().buildAndRegisterGlobal();
            this.intervalMetricReader =
                    IntervalMetricReader.builder()
                            .setMetricExporter(TESTING_METRICS_EXPORTER)
                            .setMetricProducers(Collections.singleton(sdkMeterProvider))
                            .setExportIntervalMillis(500)
                            .build();

            this.meter = GlobalMetricsProvider.getMeter("jenkins");

            // TRACES
            SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(TESTING_SPAN_EXPORTER)).build();

            this.openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(sdkTracerProvider)
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build();
            GlobalOpenTelemetry.set(openTelemetry);

            this.tracer = openTelemetry.getTracer("jenkins");
            LOGGER.log(Level.INFO, "OpenTelemetry initialized for TESTING");
        } else {
            // TODO support configurability
            String endpoint = "localhost:4317";
            boolean useTls = false;
            ManagedChannelBuilder<?> managedChannelBuilder = ManagedChannelBuilder.forTarget(endpoint);
            if (useTls) {
                managedChannelBuilder.useTransportSecurity();
            } else {
                managedChannelBuilder.usePlaintext();
            }
            ManagedChannel grpcChannel = managedChannelBuilder.build();

            // METRICS
            MetricExporter metricExporter = OtlpGrpcMetricExporter.builder().setChannel(grpcChannel).build();
            // See https://github.com/open-telemetry/opentelemetry-java/blob/v0.14.1/examples/otlp/src/main/java/io/opentelemetry/example/otlp/OtlpExporterExample.java
            SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder().buildAndRegisterGlobal();
            this.intervalMetricReader =
                    IntervalMetricReader.builder()
                            .setMetricExporter(metricExporter)
                            .setMetricProducers(Collections.singleton(sdkMeterProvider))
                            .setExportIntervalMillis(500)
                            .build();

            this.meter = GlobalMetricsProvider.getMeter("jenkins");

            // TRACES
            SpanExporter spanExporter = OtlpGrpcSpanExporter.builder().setChannel(grpcChannel).build();
            SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build();

            this.openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(sdkTracerProvider)
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build();
            GlobalOpenTelemetry.set(openTelemetry);

            this.tracer = openTelemetry.getTracer("jenkins");
            LOGGER.log(Level.INFO, "OpenTelemetry initialized");
        }
    }

    @Nonnull
    public Tracer getTracer() {
        return tracer;
    }

    @Nonnull
    public Meter getMeter() {
        return meter;
    }

    @Nonnull
    public OpenTelemetrySdk getOpenTelemetrySdk() {
        return openTelemetry;
    }


    @PreDestroy
    public void preDestroy() {
        this.openTelemetry.getTracerManagement().shutdown();
        this.intervalMetricReader.shutdown();
    }
}
