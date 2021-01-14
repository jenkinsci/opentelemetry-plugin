package io.jenkins.plugins.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Function;

public class OtelUtils {
    @Nonnull
    public static Function<Span, String> spanToDebugString() {
        return span -> {
            if (span == null) {
                return "#null#";
            } else if (span instanceof ReadableSpan) {
                ReadableSpan readableSpan = (ReadableSpan) span;
                SpanData spanData = readableSpan.toSpanData();
                return "span(" +
                        "spanId: " + spanData.getSpanId() + ", " +
                        "traceId: " + spanData.getTraceId() + ", " +
                        "parentSpanId: " + spanData.getParentSpanId() + ", " +
                        "name: " + readableSpan.getName();
            } else {
                return span.toString();
            }
        };
    }

    @Nonnull
    public static String toDebugString(@Nullable Span span) {
        return spanToDebugString().apply(span);
    }
}
