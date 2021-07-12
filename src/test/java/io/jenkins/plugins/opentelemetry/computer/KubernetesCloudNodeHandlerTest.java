/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import org.junit.Assert;
import org.junit.Test;

public class KubernetesCloudNodeHandlerTest {

    KubernetesCloudNodeHandler handler = new KubernetesCloudNodeHandler();

    @Test
    public void test_default_image() {
        verifyImage("foo", "foo");
    }

    @Test
    public void test_image_with_tag() {
        verifyImage("org/foo:1.15.10", "org/foo");
    }

    @Test
    public void test_default_image_tag() {
        verifyImageTag("foo", "latest");
    }

    @Test
    public void test_imagetag_with_tag() {
        verifyImageTag("org/foo:1.15.10", "1.15.10");
    }

    private void verifyImage(String image, String expected) {
        final String actual = handler.getImageName(image);
        Assert.assertEquals(expected, actual);
    }

    private void verifyImageTag(String image, String expected) {
        final String actual = handler.getImageTag(image);
        Assert.assertEquals(expected, actual);
    }
}