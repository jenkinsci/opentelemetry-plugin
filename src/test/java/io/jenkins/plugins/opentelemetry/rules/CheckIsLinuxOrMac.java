/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.rules;

import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Extension to check the Operating system where the test run.
 */
public class CheckIsLinuxOrMac implements BeforeEachCallback {
    @Override
    public void beforeEach(ExtensionContext context) {
        Assumptions.assumeTrue(SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_LINUX);
    }
}
