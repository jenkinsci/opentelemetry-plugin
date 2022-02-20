/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConfigPropertiesUtils {

    /**
     * Helper because there is no public implementation of the "i.o.s.a.s.ConfigProperties" interface.
     */
    public static ConfigProperties emptyConfig(){
        return new ConfigProperties() {
            @Nullable
            @Override
            public String getString(String name) {
                return null;
            }

            @Nullable
            @Override
            public Boolean getBoolean(String name) {
                return null;
            }

            @Nullable
            @Override
            public Integer getInt(String name) {
                return null;
            }

            @Nullable
            @Override
            public Long getLong(String name) {
                return null;
            }

            @Nullable
            @Override
            public Double getDouble(String name) {
                return null;
            }

            @Nullable
            @Override
            public Duration getDuration(String name) {
                return null;
            }

            @Override
            public List<String> getList(String name) {
                return Collections.emptyList();
            }

            @Override
            public Map<String, String> getMap(String name) {
                return Collections.emptyMap();
            }
        };
    }
}
