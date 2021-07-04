/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import org.junit.Assert;
import org.junit.Test;

public class CloudSpanNamingStrategyTest {

    CloudSpanNamingStrategy spanNamingStrategy = new CloudSpanNamingStrategy();

    /**
     * Unique node
     */
    @Test
    public void test_default_name() {
        verifyNodeRootSpanName("foo", "foo");
    }

    /**
     * Dynamic node
     */
    @Test
    public void test_with_dynamic_node() {
        verifyNodeRootSpanName("obs11-ubuntu-18-linux-beyyg2", "obs11-ubuntu-18-linux-{id}");
    }

    private void verifyNodeRootSpanName(String displayName, String expected) {
        final String actual = spanNamingStrategy.getNodeRootSpanName(displayName);
        Assert.assertEquals(expected, actual);
    }
}