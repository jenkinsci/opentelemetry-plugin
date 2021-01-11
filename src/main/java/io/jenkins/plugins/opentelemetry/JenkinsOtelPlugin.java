package io.jenkins.plugins.opentelemetry;

import hudson.Extension;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceAttributes;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import javax.annotation.PreDestroy;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class JenkinsOtelPlugin {
    private static Logger LOGGER = Logger.getLogger(JenkinsOtelPlugin.class.getName());

    private transient OpenTelemetrySdk openTelemetry;

    private transient Tracer tracer;

    public JenkinsOtelPlugin() {
        initialize();
    }

    protected Object readResolve() {
        initialize();
        return this;
    }

    public void initialize() {
        Resource resource = Resource.getDefault().merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "jenkins")));

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().setResource(resource).build();

        // TODO support configurability
        OtlpGrpcSpanExporter otlpGrpcSpanExporter = OtlpGrpcSpanExporter.builder().setEndpoint("localhost:4317").setUseTls(false).build();
        LoggingSpanExporter loggingSpanExporter = new LoggingSpanExporter();
        SpanProcessor spanProcessor = SpanProcessor.composite(
                /*SimpleSpanProcessor.builder(loggingSpanExporter).build(),*/
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

    public Tracer getTracer() {
        return tracer;
    }

    @PreDestroy
    public void preDestroy() {
        openTelemetry.getTracerManagement().shutdown();
    }
}
