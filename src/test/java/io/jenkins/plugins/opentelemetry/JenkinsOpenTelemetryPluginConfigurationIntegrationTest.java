package io.jenkins.plugins.opentelemetry;

import io.jenkins.plugins.opentelemetry.semconv.JenkinsMetrics;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporterUtils;
import io.opentelemetry.semconv.ServiceAttributes;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

@WithJenkins
public class JenkinsOpenTelemetryPluginConfigurationIntegrationTest {
    static {
        OpenTelemetryConfiguration.TESTING_INMEMORY_MODE = true;
    }

    @BeforeEach() public void before() {
        GlobalOpenTelemetry.resetForTest();
    }

    @After
    public void after() throws Exception {
        InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE.reset();
    }

    @Test
    @Issue("https://github.com/jenkinsci/opentelemetry-plugin/issues/1156")
    public void configLoadReconfiguresOtelSdk(JenkinsRule r) throws Exception {
        var extension = JenkinsOpenTelemetryPluginConfiguration.get();
        extension.setEndpoint("http://localhost:4317");
        extension.setServiceName("name-1");
        r.configRoundtrip();
        // Not ideal, but Descriptor.getConfigFile is protected.
        var configXmlPath = new File(Jenkins.get().getRootDir(), extension.getId() + ".xml").toPath();
        var savedConfigXml = Files.readString(configXmlPath);

        extension.setServiceName("name-2");
        r.configRoundtrip();
        await().until(() -> getServiceNameFromLastExportedMetric(JenkinsMetrics.JENKINS_QUEUE_COUNT), is("name-2"));

        // Check that overwriting the config and calling Descriptor.load is enough to reconfigure the OTel SDK.
        Files.writeString(configXmlPath, savedConfigXml);
        extension.load();
        await().until(() -> getServiceNameFromLastExportedMetric(JenkinsMetrics.JENKINS_QUEUE_COUNT), is("name-1"));
    }

    private static String getServiceNameFromLastExportedMetric(String metricName) {
        Map<String, MetricData> exportedMetrics = InMemoryMetricExporterUtils.getLastExportedMetricByMetricName(
                InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE.getFinishedMetricItems());
        var metric = Optional.ofNullable(exportedMetrics.get(metricName));
        return metric.map(m -> m.getResource().getAttributes().get(ServiceAttributes.SERVICE_NAME)).orElse(null);
    }

}
