/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.InvisibleAction;
import io.opentelemetry.api.common.AttributeKey;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @see io.opentelemetry.api.common.AttributeKey
 * @see io.opentelemetry.api.common.AttributeType
 */
public class OpenTelemetryAttributesAction extends InvisibleAction implements Serializable {

    @Serial
    private static final long serialVersionUID = 5488506456727905116L;

    private transient Map<AttributeKey<?>, Object> attributes;

    private transient Set<String> appliedToSpans;
    // If the list has any values, then only the spans on the list will get attributes.
    // If the list is empty, then there is no restriction.
    // Used to control attribute inheritance to children spans.
    private transient List<String> inheritanceAllowedSpanIdList;

    /**
     * Returns mutable OpenTelemetry attributes attached to this action.
     *
     * @return attribute map, lazily initialized
     */
    @NonNull
    public Map<AttributeKey<?>, Object> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        return attributes;
    }

    /**
     * Remember a span to which these attributes are applied.
     * @param spanId
     * @return true iff a span did not previously have these attributes applied
     */
    public boolean isNotYetAppliedToSpan(String spanId) {
        if (appliedToSpans == null) {
            appliedToSpans = new HashSet<>();
        }
        return appliedToSpans.add(spanId);
    }

    /**
     * Adds a span ID that is allowed to inherit these attributes.
     *
     * @param spanId the span ID allowed for inheritance
     */
    public void addSpanIdToInheritanceAllowedList(String spanId) {
        if (inheritanceAllowedSpanIdList == null) {
            inheritanceAllowedSpanIdList = new ArrayList<>();
        }
        inheritanceAllowedSpanIdList.add(spanId);
    }

    /**
     * Returns whether inheritance restrictions are not configured.
     *
     * @return {@code true} when no allowed span IDs are configured
     */
    public boolean inheritanceAllowedSpanIdListIsEmpty() {
        if (inheritanceAllowedSpanIdList == null) {
            return true;
        }
        return inheritanceAllowedSpanIdList.isEmpty();
    }

    /**
     * Returns whether a span ID is allowed to inherit these attributes.
     *
     * @param spanId the span ID to test
     * @return {@code true} if inheritance is allowed for the span ID
     */
    public boolean isSpanIdAllowedToInheritAttributes(String spanId) {
        if (inheritanceAllowedSpanIdList == null) {
            return false;
        }
        return inheritanceAllowedSpanIdList.contains(spanId);
    }

    /**
     * Returns a debug representation of this action and its attributes.
     *
     * @return textual representation of the action
     */
    @Override
    public String toString() {
        return "OpenTelemetryAttributesAction{" + "attributes="
                + getAttributes().entrySet().stream()
                        .map(e -> e.getKey().getKey() + "-" + e.getKey().getType() + " - " + e.getValue())
                        .collect(Collectors.joining(", "))
                + '}';
    }
}
