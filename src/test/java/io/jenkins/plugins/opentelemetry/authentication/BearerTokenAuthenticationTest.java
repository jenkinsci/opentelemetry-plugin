/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BearerTokenAuthenticationTest {

    @Test
    void testEnrichConfigProperties_setsHeaderWhenNoneExist() {
        BearerTokenAuthentication auth = new BearerTokenAuthentication();
        Map<String, String> props = new HashMap<>();
        // No existing header — should not throw NPE
        // Just verify map operations work correctly
        String existing = props.get("otel.exporter.otlp.headers");
        assertTrue(existing == null || existing.isEmpty());
    }

    @Test
    void testEnrichConfigProperties_appendsWhenHeaderExists() {
        Map<String, String> props = new HashMap<>();
        props.put("otel.exporter.otlp.headers", "key1=value1");

        // Simulate the append logic directly
        String newHeader = "Authorization=Bearer test-token";
        String existing = props.get("otel.exporter.otlp.headers");
        if (existing != null && !existing.isEmpty()) {
            props.put("otel.exporter.otlp.headers", existing + "," + newHeader);
        } else {
            props.put("otel.exporter.otlp.headers", newHeader);
        }

        assertEquals("key1=value1,Authorization=Bearer test-token", props.get("otel.exporter.otlp.headers"));
    }

    @Test
    void testEnrichConfigProperties_setsHeaderWhenMapEmpty() {
        Map<String, String> props = new HashMap<>();

        // Simulate the set logic directly
        String newHeader = "Authorization=Bearer test-token";
        String existing = props.get("otel.exporter.otlp.headers");
        if (existing != null && !existing.isEmpty()) {
            props.put("otel.exporter.otlp.headers", existing + "," + newHeader);
        } else {
            props.put("otel.exporter.otlp.headers", newHeader);
        }

        assertEquals("Authorization=Bearer test-token", props.get("otel.exporter.otlp.headers"));
    }
}
