/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsExtension;

public class GitHubClientMonitoringTest {

    @RegisterExtension
    public static JenkinsExtension jenkins = new JenkinsExtension();

    @Test
    public void testIntrospectionCode() throws Exception {
        new GitHubClientMonitoring();
    }
}
