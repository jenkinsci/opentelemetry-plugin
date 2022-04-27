/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import junit.framework.TestCase;
import org.junit.Test;

public class GitHubClientMonitoringTest extends TestCase {

    @Test
    public void testIntrospectionCode() throws Exception {
        new GitHubClientMonitoring();
    }
}