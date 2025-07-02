package io.jenkins.plugins.opentelemetry;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyCollectionOf;

import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.opentelemetry.exporter.internal.grpc.GrpcExporter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class JenkinsOtelPluginNoConfigurationIntegrationTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule loggerRule =
        new LoggerRule().record(GrpcExporter.class, Level.INFO).capture(50);

    /**
     * Explicitly disable the retry interceptor to make the error logs fast.
     * See {@link io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil#configureOtlpExporterBuilder(String, ConfigProperties, Consumer, BiConsumer, Consumer, Consumer, Consumer, BiConsumer, Consumer, Consumer)}
     */
    @Rule
    public FlagRule<String> retryDisabled = FlagRule.systemProperty("otel.java.exporter.otlp.retry.disabled", "true");

    @Test
    public void ensureNoErrorLogWhenNoConfiguration() throws Exception {
        // check no errors are logged
        await().during(Duration.ofSeconds(5))
               .atMost(Duration.ofSeconds(6))
               .pollInterval(Duration.ofMillis(500))
               .until(loggerRule::getMessages, emptyCollectionOf(String.class));

        var properties = ReconfigurableOpenTelemetry.get().getConfig();
        assertThat(properties.getString("otel.traces.exporter"), Matchers.is("none"));
        assertThat(properties.getString("otel.metrics.exporter"), Matchers.is("none"));
        assertThat(properties.getString("otel.logs.exporter"), Matchers.is("none"));
    }
}
