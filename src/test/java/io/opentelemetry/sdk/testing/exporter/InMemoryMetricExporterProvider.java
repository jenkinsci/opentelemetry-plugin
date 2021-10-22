/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.testing.exporter;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.ConfigurableMetricExporterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.testing.InMemoryMetricExporter;

public class InMemoryMetricExporterProvider implements ConfigurableMetricExporterProvider {

    public static InMemoryMetricExporter LAST_CREATED_INSTANCE;

    @Override
    public MetricExporter createExporter(ConfigProperties config) {
        LAST_CREATED_INSTANCE = InMemoryMetricExporter.create();
        return LAST_CREATED_INSTANCE;
    }

    @Override
    public String getName() {
        return "testing";
    }
}
