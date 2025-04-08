package io.opentelemetry.sdk.testing.exporter;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.logs.ConfigurableLogRecordExporterProvider;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;

public class InMemoryLogRecordExporterProvider  implements ConfigurableLogRecordExporterProvider {
    public static InMemoryLogRecordExporter LAST_CREATED_INSTANCE;

    @Override
    public LogRecordExporter createExporter(ConfigProperties configProperties) {
        LAST_CREATED_INSTANCE = InMemoryLogRecordExporter.create();
        return LAST_CREATED_INSTANCE;
    }

    @Override
    public String getName() {
        return "testing";
    }
}
