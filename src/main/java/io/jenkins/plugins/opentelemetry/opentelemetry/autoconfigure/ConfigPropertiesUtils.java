/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure;

import com.google.common.collect.Lists;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigPropertiesUtils {
    public static String prettyPrintConfiguration(ConfigProperties config) {
        List<String> attributeNames = Lists.newArrayList(
            "otel.resource.attributes", "otel.service.name",
            "otel.traces.exporter", "otel.metrics.exporter", "otel.exporter.otlp.endpoint"
            , "otel.exporter.otlp.traces.endpoint", "otel.exporter.otlp.metrics.endpoint",
            "otel.exporter.jaeger.endpoint",
            "otel.exporter.prometheus.port");

        Map<String, String> message = new LinkedHashMap<>();
        for (String attributeName: attributeNames) {
            final String attributeValue = config.getString(attributeName);
            if (attributeValue != null) {
                message.put(attributeName, attributeValue);
            }
        }
        return message.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(", "));
    }

    /**
     * Helper because there is no public implementation of the {@see ConfigProperties} interface.
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
