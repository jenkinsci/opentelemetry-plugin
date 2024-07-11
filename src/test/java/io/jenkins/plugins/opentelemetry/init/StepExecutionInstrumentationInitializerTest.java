/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure.ConfigPropertiesUtils;
import org.junit.Test;

public class StepExecutionInstrumentationInitializerTest {

    @Test
    public void testAfterConfiguration() {
        StepExecutionInstrumentationInitializer stepExecutionInstrumentationInitializer = new StepExecutionInstrumentationInitializer();
        stepExecutionInstrumentationInitializer.afterConfiguration(ConfigPropertiesUtils.emptyConfig());
    }

}