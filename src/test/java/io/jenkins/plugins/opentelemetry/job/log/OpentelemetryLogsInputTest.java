/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.job.log;

import io.jenkins.plugins.opentelemetry.BaseIntegrationTest;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assume.assumeTrue;

public class OpentelemetryLogsInputTest extends BaseIntegrationTest {

    @BeforeClass
    public static void requiresDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }

    @Test
    public void testLog() throws IOException, InterruptedException {
        OtelLogOutputStream input = new OtelLogOutputStream(new BuildInfo("foo", 1, null), null);
        input.write("foo".getBytes(StandardCharsets.UTF_8));
        Thread.sleep(5000);
        //FIXME implement assert
        input.write("foo".getBytes(StandardCharsets.UTF_8));
        input.write("foo".getBytes(StandardCharsets.UTF_8));
        input.write("foo".getBytes(StandardCharsets.UTF_8));
        input.write("foo".getBytes(StandardCharsets.UTF_8));
        Thread.sleep(5000);
        //FIXME implement assert
    }
}
