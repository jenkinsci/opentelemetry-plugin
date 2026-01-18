package io.jenkins.plugins.opentelemetry.semconv;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigurationKeyTest {

    @Test
    public void testConfigurationKey() {
        Assertions.assertEquals(
                "OTEL_EXPORTER_OTLP_CERTIFICATE", ConfigurationKey.OTEL_EXPORTER_OTLP_CERTIFICATE.asEnvVar());
        Assertions.assertEquals(
                "otel.exporter.otlp.certificate", ConfigurationKey.OTEL_EXPORTER_OTLP_CERTIFICATE.asProperty());

        Assertions.assertEquals(
                "OTEL_INSTRUMENTATION_JENKINS_RUN_METRIC_DURATION_ALLOW_LIST",
                ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_RUN_DURATION_ALLOW_LIST.asEnvVar());
        Assertions.assertEquals(
                "otel.instrumentation.jenkins.run.metric.duration.allow_list",
                ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_RUN_DURATION_ALLOW_LIST.asProperty());
    }
}
