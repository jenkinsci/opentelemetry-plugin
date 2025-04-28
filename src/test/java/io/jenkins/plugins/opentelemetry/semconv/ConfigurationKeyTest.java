package io.jenkins.plugins.opentelemetry.semconv;

import org.junit.Assert;
import org.junit.Test;


public class ConfigurationKeyTest {

    @Test
    public void testConfigurationKey(){
        Assert.assertEquals("OTEL_EXPORTER_OTLP_CERTIFICATE", ConfigurationKey.OTEL_EXPORTER_OTLP_CERTIFICATE.asEnvVar());
        Assert.assertEquals("otel.exporter.otlp.certificate", ConfigurationKey.OTEL_EXPORTER_OTLP_CERTIFICATE.asProperty());

        Assert.assertEquals("OTEL_INSTRUMENTATION_JENKINS_RUN_METRIC_DURATION_ALLOW_LIST", ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_RUN_DURATION_ALLOW_LIST.asEnvVar());
        Assert.assertEquals("otel.instrumentation.jenkins.run.metric.duration.allow_list", ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_RUN_DURATION_ALLOW_LIST.asProperty());
    }

}