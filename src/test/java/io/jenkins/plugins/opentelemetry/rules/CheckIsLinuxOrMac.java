/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.rules;

import org.apache.commons.lang.SystemUtils;
import org.junit.Assume;
import org.junit.rules.ExternalResource;

/**
 * Rule to check the Operating system where the test run.
 */
public class CheckIsLinuxOrMac extends ExternalResource {
    @Override
    protected void before() throws Throwable {
        Assume.assumeTrue(SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_LINUX);
    }
}
