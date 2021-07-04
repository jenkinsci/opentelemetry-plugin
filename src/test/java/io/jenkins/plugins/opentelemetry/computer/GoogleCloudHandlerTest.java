/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import org.junit.Assert;
import org.junit.Test;

public class GoogleCloudHandlerTest {

    GoogleCloudHandler handler = new GoogleCloudHandler();

    @Test
    public void test_default_region() {
        verifyRegion("foo", "foo");
    }

    @Test
    public void test_region() {
        verifyRegion("https://www.googleapis.com/compute/v1/projects/project-name/regions/us-central1", "us-central1");
    }

    @Test
    public void test_default_zone() {
        verifyZone("foo", "foo");
    }

    @Test
    public void test_zone() {
        verifyZone("https://www.googleapis.com/compute/v1/projects/elastic-observability/zones/us-central1-a", "us-central1-a");
    }

    @Test
    public void test_default_machineType() {
        verifyMachineType("foo", "foo");
    }

    @Test
    public void test_machineType() {
        verifyMachineType("https://www.googleapis.com/compute/v1/projects/project-name/zones/us-central1-a/machineTypes/n2-standard-2", "n2-standard-2");
    }

    private void verifyMachineType(String machineType, String expected) {
        final String actual = handler.transformMachineType(machineType);
        Assert.assertEquals(expected, actual);
    }

    private void verifyRegion(String region, String expected) {
        final String actual = handler.transformRegion(region);
        Assert.assertEquals(expected, actual);
    }

    private void verifyZone(String zone, String expected) {
        final String actual = handler.transformZone(zone);
        Assert.assertEquals(expected, actual);
    }
}