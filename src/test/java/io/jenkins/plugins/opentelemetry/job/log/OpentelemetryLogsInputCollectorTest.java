/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.job.log;

import org.junit.Before;
import org.junit.BeforeClass;
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
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class OpentelemetryLogsInputCollectorTest {
    public static final int OTEL_PORT = 4317;
    private static final Logger LOGGER = Logger.getLogger(OpentelemetryLogsInputCollectorTest.class.getName());
    private static final File workdir = new File("/tmp");
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
    public void setUp() {
    }

    @Test
    public void testLog() throws IOException, InterruptedException {
        OtelLogOutputStream input = new OtelLogOutputStream(new BuildInfo("foo", 1, null), null);
        input.write("foo00".getBytes(StandardCharsets.UTF_8));
        Thread.sleep(5000);
        assertTrue(otelCollector.getLogs().contains("foo00"));
        input.write("foo01".getBytes(StandardCharsets.UTF_8));
        input.write("foo02".getBytes(StandardCharsets.UTF_8));
        input.write("foo03".getBytes(StandardCharsets.UTF_8));
        input.write("foo04".getBytes(StandardCharsets.UTF_8));
        Thread.sleep(5000);
        assertTrue(otelCollector.getLogs().contains("foo01"));
        assertTrue(otelCollector.getLogs().contains("foo02"));
        assertTrue(otelCollector.getLogs().contains("foo03"));
        assertTrue(otelCollector.getLogs().contains("foo04"));
        for (int i = 0; i < 20; i++) {
            input.write(("bar" + i).getBytes(StandardCharsets.UTF_8));
        }
        Thread.sleep(5000);
        for (int i = 0; i < 20; i++) {
            assertTrue(otelCollector.getLogs().contains("bar" + i));
        }
        //TODO implement checks over the /tmp/tests.json traces file
    }
}
