/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.testing.exporter;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class InMemoryMetricExporter implements MetricExporter {

    private final Logger logger = Logger.getLogger(InMemoryMetricExporter.class.getName());

    private final List<MetricData> metrics = new ArrayList<>();
    private boolean isStopped = false;

    @Override
    public synchronized CompletableResultCode  export(Collection<MetricData> metrics) {
        logger.log(Level.FINER, () -> "Export " + metrics.stream().map(metric -> metric.getName()).collect(Collectors.joining(", ")));
        if (isStopped) {
            return CompletableResultCode.ofFailure();
        }
        this.metrics.addAll(metrics);
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public synchronized CompletableResultCode shutdown() {
        this.metrics.clear();
        this.isStopped = true;
        return CompletableResultCode.ofSuccess();
    }

    @Nonnull
    public synchronized List<MetricData> getExportedMetricItems(){
        return Collections.unmodifiableList(new ArrayList<>(metrics));
    }

    @Nonnull
    public synchronized Map<String, MetricData> getLastExportedMetricByMetricName(){
        Map<String, MetricData> result = new HashMap<>();
        for(MetricData metricData: this.metrics) {
            result.put(metricData.getName(), metricData);
        }
        return result;
    }

    public synchronized void reset(){
        this.isStopped = false;
        this.metrics.clear();
    }

    private InMemoryMetricExporter() {
    }

    public static InMemoryMetricExporter create() {
        return new InMemoryMetricExporter();
    }
}
