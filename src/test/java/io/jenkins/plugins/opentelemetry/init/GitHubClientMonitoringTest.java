/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import junit.framework.TestCase;
import org.jenkinsci.plugins.github_branch_source.Connector;
import org.junit.Test;

import java.lang.reflect.Field;

public class GitHubClientMonitoringTest extends TestCase {

    @Test
    public void testIntrospectionCode() throws Exception {
        Field connectorReverseLookupField = Connector.class.getDeclaredField("reverseLookup");
        connectorReverseLookupField.setAccessible(true);
    }

}