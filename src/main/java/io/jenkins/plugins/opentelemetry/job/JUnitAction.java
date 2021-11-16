/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.model.InvisibleAction;
import io.opentelemetry.api.common.AttributeKey;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @see AttributeKey
 * @see io.opentelemetry.api.common.AttributeType
 */
public class JUnitAction extends InvisibleAction {

    private Map<AttributeKey<?>, Object> attributes;

    @Nonnull
    public Map<AttributeKey<?>, Object> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        return attributes;
    }

    @Override
    public String toString() {
        return "JUnitAction{" +
                "attributes=" + getAttributes().entrySet().stream().map(e -> e.getKey().getKey() + "-" + e.getKey().getType() + " - " + e.getValue()).collect(Collectors.joining(", ")) +
                '}';
    }
}
