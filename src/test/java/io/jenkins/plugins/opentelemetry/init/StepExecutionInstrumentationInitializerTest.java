/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class StepExecutionInstrumentationInitializerTest {

    @Test
    void testAfterConfiguration() {
        StepExecutionInstrumentationInitializer stepExecutionInstrumentationInitializer =
                new StepExecutionInstrumentationInitializer();
        stepExecutionInstrumentationInitializer.afterConfiguration(
                DefaultConfigProperties.createFromMap(Collections.emptyMap()));
    }
}
