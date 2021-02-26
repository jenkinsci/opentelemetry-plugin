/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.*;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Ignore
public class OpenTelemetrySdkProviderTest {

    private Logger logger = Logger.getLogger(OpenTelemetrySdkProviderTest.class.getName());

    @Test
    public void test() throws Exception {
        OpenTelemetrySdkProvider openTelemetrySdkProvider = new OpenTelemetrySdkProvider();
        openTelemetrySdkProvider.postConstruct();
        openTelemetrySdkProvider.initializeNoOp();


        Tracer tracer = openTelemetrySdkProvider.getTracer();
        Meter meter = openTelemetrySdkProvider.getMeter();
        OpenTelemetrySdk openTelemetry = openTelemetrySdkProvider.getOpenTelemetrySdk();

        LongCounter myMetric = meter.longCounterBuilder("my-metric").build();
        myMetric.add(1);
        System.out.println("myMetric");

        SpanBuilder rootSpanBuilder = tracer.spanBuilder("ci.pipeline.run")
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, "my-pipeline")
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_NAME, "my pipeline")
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, 12l);

        Span rootSpan = rootSpanBuilder.startSpan();
        System.out.println("Root span object: " + rootSpan.getClass() + ", " + rootSpan);
        SpanData rootSpanData = ((ReadableSpan) rootSpan).toSpanData();

        try (Scope scope = rootSpan.makeCurrent()) {
            Thread.sleep(1_000);

            System.out.println("OPEN TELEMETRY FORCE FLUSH");
            CompletableResultCode completableResultCode = openTelemetry.getSdkTracerProvider().forceFlush();

            completableResultCode.join(5, TimeUnit.SECONDS);
        } finally {
            rootSpan.end();
        }


        openTelemetry.getSdkTracerProvider().shutdown();
    }

    @After
    public void after() {

    }

    @BeforeClass
    public static void beforeClass() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterClass
    public static void afterClass() {
        GlobalOpenTelemetry.resetForTest();
    }
}
