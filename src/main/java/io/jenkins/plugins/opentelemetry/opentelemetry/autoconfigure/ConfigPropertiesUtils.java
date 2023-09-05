/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;

import java.util.Collections;

public class ConfigPropertiesUtils {

    /**
     * Helper because there is no public implementation of the "i.o.s.a.s.ConfigProperties" interface.
     */
    public static ConfigProperties emptyConfig(){
        return DefaultConfigProperties.createForTest(Collections.emptyMap());
    }
}
