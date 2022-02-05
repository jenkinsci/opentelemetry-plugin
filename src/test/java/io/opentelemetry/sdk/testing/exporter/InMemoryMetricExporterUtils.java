/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.testing.exporter;

import io.opentelemetry.sdk.metrics.data.MetricData;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryMetricExporterUtils {

    /**
     *
     * @see InMemoryMetricExporter#getFinishedMetricItems()
     */
    @Nonnull
    public static Map<String, MetricData> getLastExportedMetricByMetricName(@Nonnull List<MetricData> metrics){
        Map<String, MetricData> result = new HashMap<>();
        for(MetricData metricData: metrics) {
            result.put(metricData.getName(), metricData);
        }
        return result;
    }

}
