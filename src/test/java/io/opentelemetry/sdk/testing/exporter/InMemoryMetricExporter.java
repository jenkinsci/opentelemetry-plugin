package io.opentelemetry.sdk.testing.exporter;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;

import java.util.Collection;

public class InMemoryMetricExporter implements MetricExporter {
    @Override
    public CompletableResultCode export(Collection<MetricData> metrics) {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    private InMemoryMetricExporter() {
    }

    public static InMemoryMetricExporter create() {
        return new InMemoryMetricExporter();
    }
}
