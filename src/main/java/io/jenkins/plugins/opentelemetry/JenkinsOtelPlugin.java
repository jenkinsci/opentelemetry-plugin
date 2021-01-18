package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
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
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceAttributes;
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
    private static Logger LOGGER = Logger.getLogger(JenkinsOtelPlugin.class.getName());

    @VisibleForTesting
    protected transient OpenTelemetrySdk openTelemetry;

    @VisibleForTesting
    protected transient Tracer tracer;

    @VisibleForTesting
    protected transient Meter meter;

    @VisibleForTesting
    protected transient IntervalMetricReader intervalMetricReader;

    public JenkinsOtelPlugin() {
        initialize();
    }

    protected Object readResolve() {
        initialize();
        return this;
    }

    public void initialize() {
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

        SpanExporter spanExporter = OtlpGrpcSpanExporter.builder().setChannel(grpcChannel).build();
        MetricExporter metricExporter = OtlpGrpcMetricExporter.builder().setChannel(grpcChannel).build();
        initialize(spanExporter, metricExporter);
    }

    @VisibleForTesting
    protected void initialize(SpanExporter spanExporter, MetricExporter metricExporter) {
        // TRACES
        Resource resource = Resource.getDefault().merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "jenkins")));
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().setResource(resource).addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build();

        // METRICS
        // See https://github.com/open-telemetry/opentelemetry-java/blob/v0.14.1/examples/otlp/src/main/java/io/opentelemetry/example/otlp/OtlpExporterExample.java
        SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder().setResource(resource).buildAndRegisterGlobal();
        this.intervalMetricReader =
                IntervalMetricReader.builder()
                        .setMetricExporter(metricExporter)
                        .setMetricProducers(Collections.singleton(sdkMeterProvider))
                        .setExportIntervalMillis(500)
                        .build();


        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GlobalOpenTelemetry.set(openTelemetry);

        this.tracer = openTelemetry.getTracer("jenkins");
        this.meter = GlobalMetricsProvider.getMeter("jenkins");

        LOGGER.log(Level.INFO, "OpenTelemetry initialized");
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
    public OpenTelemetrySdk getOpenTelemetry() {
        return openTelemetry;
    }

    @PreDestroy
    public void preDestroy() {
        this.openTelemetry.getTracerManagement().shutdown();
        this.intervalMetricReader.shutdown();
    }
}
