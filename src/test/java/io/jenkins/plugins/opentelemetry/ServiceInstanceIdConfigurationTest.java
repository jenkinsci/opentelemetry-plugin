package io.jenkins.plugins.opentelemetry;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import hudson.ExtensionList;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporterProvider;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Integration tests for service.instance.id configuration override via system property.
 * Tests the ability to programmatically override service.instance.id for environments
 * like CloudBees CI HA where multiple controller replicas need unique instance IDs.
 *
 * @see <a href="https://github.com/jenkinsci/opentelemetry-plugin/issues/1154">Issue #1154</a>
 */
@WithJenkins
@Issue("JENKINS-1154")
public class ServiceInstanceIdConfigurationTest {

    private static final String SYSTEM_PROPERTY_NAME = "io.jenkins.plugins.opentelemetry.service.instance.id";
    private JenkinsOpenTelemetryPluginConfiguration plugin;

    @Before
    public void setup() {
        plugin = ExtensionList.lookupSingleton(JenkinsOpenTelemetryPluginConfiguration.class);
        plugin.setServiceName("test-service");
        plugin.setServiceNamespace("test-namespace");
    }

    @After
    public void teardown() {
        // Clean up system property after each test to prevent interference
        System.clearProperty(SYSTEM_PROPERTY_NAME);
        if (InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE != null) {
            InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE.reset();
        }
    }

    /**
     * Test that when no system property is set, service.instance.id uses Jenkins.getLegacyInstanceId()
     * This ensures backward compatibility with existing deployments.
     */
    @Test
    public void testDefaultServiceInstanceIdUsesLegacyId() {
        // Arrange: Ensure system property is not set
        System.clearProperty(SYSTEM_PROPERTY_NAME);
        String expectedInstanceId = Jenkins.get().getLegacyInstanceId();

        // Act: Configure the OpenTelemetry SDK
        plugin.configureOpenTelemetrySdk();

        // Wait for metrics to be exported
        await().atMost(5, TimeUnit.SECONDS).until(() -> !InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE
                .getFinishedMetricItems()
                .isEmpty());

        // Assert: Verify service.instance.id matches legacy instance ID
        String actualInstanceId = getServiceInstanceIdFromLastExportedMetric();
        assertThat(
                "service.instance.id should use Jenkins.getLegacyInstanceId() when no system property is set",
                actualInstanceId,
                is(expectedInstanceId));
    }

    /**
     * Test that service.instance.id can be overridden via system property.
     * This enables CloudBees CI HA replicas to have unique instance IDs.
     */
    @Test
    public void testServiceInstanceIdCanBeOverriddenViaSystemProperty() {
        // Arrange: Set custom instance ID via system property
        String customInstanceId = "custom-ha-replica-01";
        System.setProperty(SYSTEM_PROPERTY_NAME, customInstanceId);

        // Act: Configure the OpenTelemetry SDK
        plugin.configureOpenTelemetrySdk();

        // Wait for metrics to be exported
        await().atMost(5, TimeUnit.SECONDS).until(() -> !InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE
                .getFinishedMetricItems()
                .isEmpty());

        // Assert: Verify service.instance.id uses the custom value
        String actualInstanceId = getServiceInstanceIdFromLastExportedMetric();
        assertThat(
                "service.instance.id should use system property value when set",
                actualInstanceId,
                is(customInstanceId));
    }

    /**
     * Test that empty system property value falls back to legacy instance ID.
     * This ensures robustness against misconfiguration.
     */
    @Test
    public void testEmptySystemPropertyFallsBackToLegacyId() {
        // Arrange: Set system property to empty string
        System.setProperty(SYSTEM_PROPERTY_NAME, "");
        String expectedInstanceId = Jenkins.get().getLegacyInstanceId();

        // Act: Configure the OpenTelemetry SDK
        plugin.configureOpenTelemetrySdk();

        // Wait for metrics to be exported
        await().atMost(5, TimeUnit.SECONDS).until(() -> !InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE
                .getFinishedMetricItems()
                .isEmpty());

        // Assert: Verify service.instance.id falls back to legacy ID
        String actualInstanceId = getServiceInstanceIdFromLastExportedMetric();
        assertThat(
                "service.instance.id should fall back to Jenkins.getLegacyInstanceId() when system property is empty",
                actualInstanceId,
                is(expectedInstanceId));
    }

    /**
     * Test that whitespace-only system property value falls back to legacy instance ID.
     * This validates the trim().isEmpty() logic in the implementation.
     */
    @Test
    public void testWhitespaceSystemPropertyFallsBackToLegacyId() {
        // Arrange: Set system property to whitespace-only string
        System.setProperty(SYSTEM_PROPERTY_NAME, "   ");
        String expectedInstanceId = Jenkins.get().getLegacyInstanceId();

        // Act: Configure the OpenTelemetry SDK
        plugin.configureOpenTelemetrySdk();

        // Wait for metrics to be exported
        await().atMost(5, TimeUnit.SECONDS).until(() -> !InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE
                .getFinishedMetricItems()
                .isEmpty());

        // Assert: Verify service.instance.id falls back to legacy ID
        String actualInstanceId = getServiceInstanceIdFromLastExportedMetric();
        assertThat(
                "service.instance.id should fall back to Jenkins.getLegacyInstanceId() when system property is whitespace-only",
                actualInstanceId,
                is(expectedInstanceId));
    }

    /**
     * Helper method to extract service.instance.id from the last exported metric.
     * Inspects the Resource attributes of the most recent metric export.
     *
     * @return The service.instance.id value from the last exported metric, or null if not found
     */
    private String getServiceInstanceIdFromLastExportedMetric() {
        List<MetricData> metrics = InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE.getFinishedMetricItems();
        assertThat("At least one metric should be exported", metrics, notNullValue());
        assertThat("Metrics list should not be empty", !metrics.isEmpty());

        // Get the last exported metric
        MetricData lastMetric = metrics.get(metrics.size() - 1);
        Resource resource = lastMetric.getResource();
        Attributes attributes = resource.getAttributes();

        // Extract service.instance.id from resource attributes
        return attributes.get(ServiceIncubatingAttributes.SERVICE_INSTANCE_ID);
    }
}
