/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.job.log;

import hudson.ExtensionList;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.opentelemetry.OpenTelemetryConfiguration;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.opentelemetry.sdk.common.Clock;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static com.google.common.base.Verify.verify;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class OpentelemetryLogsInputCollectorTest {
    public static final int OTEL_PORT = 4317;
    private static final Logger LOGGER = Logger.getLogger(OpentelemetryLogsInputCollectorTest.class.getName());
    private static final File workdir = new File("/tmp");

    @ClassRule
    public static JenkinsConfiguredWithCodeRule jenkinsRule = new JenkinsConfiguredWithCodeRule();

    static OpenTelemetrySdkProvider openTelemetrySdkProvider;

    @Rule
    public GenericContainer otelCollector = new GenericContainer("otel/opentelemetry-collector-contrib-dev:latest")
        .withClasspathResourceMapping("otel-collector.yml", "/otel-collector.yml", BindMode.READ_ONLY)
        .withFileSystemBind(workdir.getAbsolutePath(), "/tmp", BindMode.READ_WRITE)
        .withCommand("--config /otel-collector.yml")
        .waitingFor(Wait.forLogMessage("^.*Everything is ready.*", 1)).withExposedPorts(OTEL_PORT)
        .withStartupTimeout(Duration.ofMinutes(1));

    @BeforeClass
    public static void requiresDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }

    @Before
    public void setup() throws Exception {
        ExtensionList<OpenTelemetrySdkProvider> openTelemetrySdkProviders = jenkinsRule.getInstance().getExtensionList(OpenTelemetrySdkProvider.class);
        verify(openTelemetrySdkProviders.size() == 1, "Number of openTelemetrySdkProviders: %s", openTelemetrySdkProviders.size());
        openTelemetrySdkProvider = openTelemetrySdkProviders.get(0);
        Map<String, String> properties = new HashMap<>();
        properties.put("otel.traces.exporter", "none");
        properties.put("otel.metrics.exporter", "none");
        properties.put("otel.imr.export.interval", "10ms");
        properties.put("otel.logs.exporter", "otlp");
        openTelemetrySdkProvider.initialize(new OpenTelemetryConfiguration(
            Optional.of("http://localhost:" + otelCollector.getMappedPort(OTEL_PORT)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of("OtelLogTest"),
            Optional.of("OtelJenkinsTest"),
            Optional.empty(),
            properties
        ));
    }

    @Test
    public void testLog() throws IOException, InterruptedException {
        OtelLogOutputStream input = new OtelLogOutputStream(
            new BuildInfo("foo", 1, null, null, null),
            null,
            openTelemetrySdkProvider.getLogEmitter(), Clock.getDefault());
        input.write("foo00\n".getBytes(StandardCharsets.UTF_8));
        Thread.sleep(1000);
        assertTrue(otelCollector.getLogs().contains("Body: foo00"));
        input.write("foo01\n".getBytes(StandardCharsets.UTF_8));
        input.write("foo02\n".getBytes(StandardCharsets.UTF_8));
        input.write("foo03\n".getBytes(StandardCharsets.UTF_8));
        input.write("foo04\n".getBytes(StandardCharsets.UTF_8));
        Thread.sleep(1000);
        assertTrue(otelCollector.getLogs().contains("Body: foo01"));
        assertTrue(otelCollector.getLogs().contains("Body: foo02"));
        assertTrue(otelCollector.getLogs().contains("Body: foo03"));
        assertTrue(otelCollector.getLogs().contains("Body: foo04"));
        for (int i = 0; i < 20; i++) {
            input.write(("bar" + i + "\n").getBytes(StandardCharsets.UTF_8));
        }
        Thread.sleep(1000);
        for (int i = 0; i < 20; i++) {
            assertTrue(otelCollector.getLogs().contains("Body: bar" + i));
        }
    }
}
