package io.jenkins.plugins;

import io.jenkins.plugins.opentelemetry.JenkinsOtelPlugin;
import io.jenkins.plugins.opentelemetry.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.trace.OtelTraceService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class OpenTelemetryTest {

    private Logger logger = Logger.getLogger(OpenTelemetryTest.class.getName());

    @Test
    public void test() throws Exception {
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().build();
        sdkTracerProvider.addSpanProcessor(SimpleSpanProcessor.builder(new LoggingSpanExporter()).build());

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GlobalOpenTelemetry.set(openTelemetry);

        Tracer tracer = openTelemetry.getTracer("jenkins");

        SpanBuilder rootSpanBuilder = tracer.spanBuilder("ci.pipeline.run")
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_TYPE, "jenkins")
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, "my-pipeline")
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_NAME, "my pipeline")
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, 12l);

        Span rootSpan = rootSpanBuilder.startSpan();
        System.out.println("Root span object: " + rootSpan.getClass() + ", " + rootSpan);
        SpanData rootSpanData = ((ReadableSpan) rootSpan).toSpanData();

        try (Scope scope = rootSpan.makeCurrent()) {
            Thread.sleep(1_000);

            System.out.println("OPEN TELEMETRY FORCE FLUSH");
            CompletableResultCode completableResultCode = openTelemetry.getTracerManagement().forceFlush();

            completableResultCode.join(5, TimeUnit.SECONDS);
        } finally {
            rootSpan.end();
        }

        openTelemetry.getTracerManagement().shutdown();
    }

    @After
    public void after() {

    }
}
