/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure;

import com.google.common.collect.Lists;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

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
}
