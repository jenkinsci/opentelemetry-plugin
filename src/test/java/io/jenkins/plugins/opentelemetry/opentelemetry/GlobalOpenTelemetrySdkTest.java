/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporterProvider;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class GlobalOpenTelemetrySdkTest {

    @Test
    public void forceFlushLogsExportsTheQueuedRecordsBeforeItReturns() {
        try {
            Map<String, String> config = new HashMap<>();
            config.put("otel.traces.exporter", "none");
            config.put("otel.metrics.exporter", "none");
            config.put("otel.logs.exporter", "testing");
            // the batch processor would not export on its own during the test
            config.put("otel.blrp.schedule.delay", "600000");
            GlobalOpenTelemetrySdk.configure(config, Map.of("service.name", "force-flush-test"), false);
            InMemoryLogRecordExporter exporter = InMemoryLogRecordExporterProvider.LAST_CREATED_INSTANCE;

            GlobalOpenTelemetrySdk.getOtelLogger()
                    .logRecordBuilder()
                    .setBody("queued record")
                    .emit();
            assertTrue(exporter.getFinishedLogRecordItems().isEmpty(), "record still queued in the batch processor");

            assertTrue(GlobalOpenTelemetrySdk.forceFlushLogs(10_000), "flush result");
            List<LogRecordData> exported = exporter.getFinishedLogRecordItems();
            assertEquals(1, exported.size(), "exported records");
            assertEquals("queued record", exported.get(0).getBodyValue().asString());
        } finally {
            GlobalOpenTelemetrySdk.get().close();
        }
    }

    @Test
    public void forceFlushLogsWithoutSdkSucceeds() {
        GlobalOpenTelemetrySdk.get().close();
        assertTrue(GlobalOpenTelemetrySdk.forceFlushLogs(1_000));
    }

    @Test
    public void forceFlushReturnsFalseWhenTheFlushFails() {
        try (SdkLoggerProvider loggerProvider = loggerProviderFlushingWith(CompletableResultCode::ofFailure)) {
            assertFalse(GlobalOpenTelemetrySdk.forceFlush(loggerProvider, 10_000));
        }
    }

    @Test
    public void forceFlushReturnsFalseWithinItsTimeoutWhenTheFlushDoesNotFinish() {
        try (SdkLoggerProvider loggerProvider = loggerProviderFlushingWith(CompletableResultCode::new)) {
            long start = System.nanoTime();
            assertFalse(GlobalOpenTelemetrySdk.forceFlush(loggerProvider, 200));
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
            assertTrue(elapsedMillis >= 190 && elapsedMillis < 5_000, "returned after " + elapsedMillis + "ms");
        }
    }

    @Test
    public void forceFlushReturnsTrueWhenTheFlushSucceeds() {
        try (SdkLoggerProvider loggerProvider = loggerProviderFlushingWith(CompletableResultCode::ofSuccess)) {
            assertTrue(GlobalOpenTelemetrySdk.forceFlush(loggerProvider, 10_000));
        }
    }

    private static SdkLoggerProvider loggerProviderFlushingWith(Supplier<CompletableResultCode> flushResult) {
        return SdkLoggerProvider.builder()
                .addLogRecordProcessor(new LogRecordProcessor() {
                    @Override
                    public void onEmit(Context context, ReadWriteLogRecord logRecord) {}

                    @Override
                    public CompletableResultCode forceFlush() {
                        return flushResult.get();
                    }

                    @Override
                    public CompletableResultCode shutdown() {
                        return CompletableResultCode.ofSuccess();
                    }
                })
                .build();
    }

    @Test
    public void testNotSdkConfigured() {
        try {
            @SuppressWarnings("unused")
            ReconfigurableOpenTelemetry openTelemetry = GlobalOpenTelemetrySdk.get();
            @SuppressWarnings("unused")
            Logger otelLogger = GlobalOpenTelemetrySdk.getOtelLogger();
        } finally {
            GlobalOpenTelemetrySdk.get().close();
        }
    }

    @Test
    public void testSdkSetConfigurationOnce() {
        GlobalOpenTelemetrySdk.currentSdkConfiguration =
                new GlobalOpenTelemetrySdk.OtelSdkConfiguration(Collections.emptyMap(), Collections.emptyMap());
        try {
            Map<String, String> config = new HashMap<>();
            config.put("otel.traces.exporter", "none");
            config.put("otel.metrics.exporter", "none");
            config.put("otel.logs.exporter", "none");
            Resource resource = Resource.builder()
                    .put(ServiceAttributes.SERVICE_NAME, "jenkins")
                    .put(ServiceAttributes.SERVICE_VERSION, "1.2.3")
                    .put(ServiceIncubatingAttributes.SERVICE_NAMESPACE, "cicd")
                    .build();

            Map<String, String> resourceAttributes = new HashMap<>();
            resource.getAttributes().forEach((k, v) -> resourceAttributes.put(k.getKey(), v.toString()));

            int configurationCountBefore = GlobalOpenTelemetrySdk.configurationCounter.get();
            GlobalOpenTelemetrySdk.configure(config, resourceAttributes, false);
            assertEquals(
                    configurationCountBefore + 1,
                    GlobalOpenTelemetrySdk.configurationCounter.get(),
                    "Configuration counter");
        } finally {
            GlobalOpenTelemetrySdk.get().close();
        }
    }

    @Test
    public void testSdkSetConfiguration_twice_with_same_configProperties_and_resourceAttributes() {
        try {
            Map<String, String> config = new HashMap<>();
            config.put("otel.traces.exporter", "none");
            config.put("otel.metrics.exporter", "none");
            config.put("otel.logs.exporter", "none");
            Resource resource = Resource.builder()
                    .put(ServiceAttributes.SERVICE_NAME, "jenkins")
                    .put(ServiceAttributes.SERVICE_VERSION, "1.2.3")
                    .put(ServiceIncubatingAttributes.SERVICE_NAMESPACE, "cicd")
                    .build();

            Map<String, String> resourceAttributes = new HashMap<>();
            resource.getAttributes().forEach((k, v) -> resourceAttributes.put(k.getKey(), v.toString()));

            int configurationCountBefore = GlobalOpenTelemetrySdk.configurationCounter.get();

            // CONFIGURE ONCE
            {
                GlobalOpenTelemetrySdk.configure(config, resourceAttributes, false);
                assertEquals(
                        configurationCountBefore + 1,
                        GlobalOpenTelemetrySdk.configurationCounter.get(),
                        "Configuration counter");
            }

            // CONFIGURE A SECOND TIME WITH SAME CONFIGURATION
            {
                GlobalOpenTelemetrySdk.configure(config, resourceAttributes, false);
                // verify has been configured just once
                assertEquals(
                        configurationCountBefore + 1,
                        GlobalOpenTelemetrySdk.configurationCounter.get(),
                        "Configuration counter");
            }
        } finally {
            GlobalOpenTelemetrySdk.get().close();
        }
    }

    @Test
    public void testSdkSetConfiguration_twice_with_same_configProperties_and_different_resourceAttributes() {
        try {
            Map<String, String> config = new HashMap<>();
            config.put("otel.traces.exporter", "none");
            config.put("otel.metrics.exporter", "none");
            config.put("otel.logs.exporter", "none");

            int configurationCountBefore = GlobalOpenTelemetrySdk.configurationCounter.get();

            // CONFIGURE ONCE
            {
                final Resource resource = Resource.builder()
                        .put(ServiceAttributes.SERVICE_NAME, "jenkins")
                        .put(ServiceAttributes.SERVICE_VERSION, "1.2.3")
                        .put(ServiceIncubatingAttributes.SERVICE_NAMESPACE, "cicd")
                        .build();

                final Map<String, String> resourceAttributes = new HashMap<>();
                resource.getAttributes().forEach((k, v) -> resourceAttributes.put(k.getKey(), v.toString()));

                GlobalOpenTelemetrySdk.configure(config, resourceAttributes, false);
                assertEquals(
                        configurationCountBefore + 1,
                        GlobalOpenTelemetrySdk.configurationCounter.get(),
                        "Configuration counter");
            }

            // CONFIGURE A SECOND TIME WITH SAME CONFIG PROPERTIES AND DIFFERENT RESOURCE ATTRIBUTES
            {
                final Resource differentResource = Resource.builder()
                        .put(ServiceAttributes.SERVICE_NAME, "jenkins")
                        .put(ServiceAttributes.SERVICE_VERSION, "1.2.3")
                        .put(ServiceAttributes.SERVICE_NAME.getKey(), "another_namespace")
                        .build();
                final Map<String, String> differentResourceAttributes = new HashMap<>();
                differentResource
                        .getAttributes()
                        .forEach((k, v) -> differentResourceAttributes.put(k.getKey(), v.toString()));

                GlobalOpenTelemetrySdk.configure(config, differentResourceAttributes, false);
                // verify has been configured twice
                assertEquals(
                        configurationCountBefore + 2,
                        GlobalOpenTelemetrySdk.configurationCounter.get(),
                        "Configuration counter");
            }
        } finally {
            GlobalOpenTelemetrySdk.get().close();
        }
    }

    @Test
    public void testSdkSetConfiguration_twice_with_different_configProperties_and_same_resourceAttributes() {
        try {

            final Resource resource = Resource.builder()
                    .put(ServiceAttributes.SERVICE_NAME, "jenkins")
                    .put(ServiceAttributes.SERVICE_VERSION, "1.2.3")
                    .put(ServiceIncubatingAttributes.SERVICE_NAMESPACE, "cicd")
                    .build();

            final Map<String, String> resourceAttributes = new HashMap<>();
            resource.getAttributes().forEach((k, v) -> resourceAttributes.put(k.getKey(), v.toString()));

            int configurationCountBefore = GlobalOpenTelemetrySdk.configurationCounter.get();

            // CONFIGURE ONCE
            {
                Map<String, String> config = new HashMap<>();
                config.put("otel.traces.exporter", "none");
                config.put("otel.metrics.exporter", "none");
                config.put("otel.logs.exporter", "none");

                GlobalOpenTelemetrySdk.configure(config, resourceAttributes, false);
                assertEquals(
                        configurationCountBefore + 1,
                        GlobalOpenTelemetrySdk.configurationCounter.get(),
                        "Configuration counter");
            }

            // CONFIGURE A SECOND TIME WITH DIFFERENT CONFIG PROPERTIES AND SAME RESOURCE ATTRIBUTES
            {
                Map<String, String> differentConfig = new HashMap<>();
                differentConfig.put("otel.traces.exporter", "none");
                differentConfig.put("otel.metrics.exporter", "none");
                differentConfig.put("otel.logs.exporter", "none");
                differentConfig.put("a", "b");

                GlobalOpenTelemetrySdk.configure(differentConfig, resourceAttributes, false);
                // verify has been configured twice
                assertEquals(
                        configurationCountBefore + 2,
                        GlobalOpenTelemetrySdk.configurationCounter.get(),
                        "Configuration counter");
            }
        } finally {
            GlobalOpenTelemetrySdk.get().close();
        }
    }
}
