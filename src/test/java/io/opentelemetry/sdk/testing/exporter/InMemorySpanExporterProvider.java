/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.testing.exporter;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class InMemorySpanExporterProvider implements ConfigurableSpanExporterProvider {

    public static InMemorySpanExporter LAST_CREATED_INSTANCE;

    @Override
    public SpanExporter createExporter(ConfigProperties config) {
        LAST_CREATED_INSTANCE =  InMemorySpanExporter.create();
        return LAST_CREATED_INSTANCE;
    }

    @Override
    public String getName() {
        return "testing";
    }


}
