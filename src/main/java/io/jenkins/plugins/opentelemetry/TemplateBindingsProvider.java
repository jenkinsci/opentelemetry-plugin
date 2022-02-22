/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provide bindings for Groovy {@link groovy.text.Template}.
 *
 * Bindings are intended to be used in {@link groovy.text.Template#make(Map)}.
 */
public interface TemplateBindingsProvider {
    static TemplateBindingsProvider empty() {
        return () -> Collections.emptyMap();
    }
    static TemplateBindingsProvider of(Map<String, String> bindings) {
        return () -> bindings;
    }

    static TemplateBindingsProvider of(String key, String value) {
        return of(Collections.singletonMap(key, value));
    }
    static TemplateBindingsProvider of(String key1, String value1, String key2, String value2) {
        Map<String, String> map = new LinkedHashMap<>(2);
        map.put(key1, value1);
        map.put(key2, value2);
        return of(map);
    }

    /**
     * Passed {@code bindings} overwrite the values of the passed {@code templateBindingsProvider}
     */
    static TemplateBindingsProvider compose(TemplateBindingsProvider templateBindingsProvider, Map<String, String> bindings) {
        return () -> {
            Map<String, String> newBindings = new HashMap<>(templateBindingsProvider.getBindings());
            newBindings.putAll(bindings);
            return newBindings;
        };
    }

    Map<String, String> getBindings();
}
