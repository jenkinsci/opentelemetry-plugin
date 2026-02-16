/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class GitHubClientMonitoringTest {

    private JenkinsRule r;

    @BeforeEach
    public void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Test
    public void testIntrospectionCode() throws Exception {
        new GitHubClientMonitoring();
    }
}
