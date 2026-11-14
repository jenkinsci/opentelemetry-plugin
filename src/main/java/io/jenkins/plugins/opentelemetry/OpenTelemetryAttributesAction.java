/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.InvisibleAction;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/**
 * @see io.opentelemetry.api.common.AttributeKey
 * @see io.opentelemetry.api.common.AttributeType
 */
public class OpenTelemetryAttributesAction extends InvisibleAction implements Serializable {

    @Serial
    private static final long serialVersionUID = 5488506456727905116L;

    /**
     * Replaced as a whole by {@link #replaceAttributes(Map)}, never cleared in place, so a reader that iterates
     * the map returned by {@link #getAttributes()} sees either the old or the new attributes, never a mix.
     */
    private transient Map<AttributeKey<?>, Object> attributes;

    /**
     * Spans to which these attributes have been applied.
     * The action can live as long as the agent it is attached to, so it must not keep spans that have ended.
     * Spans are held weakly: the plugin keeps a strong reference to a span while it is open
     * and drops it when the span ends, so an open span stays here and an ended span goes away on the next GC.
     * {@link Span} implementations do not override equals/hashCode, so the set compares spans by identity.
     */
    private transient Set<Span> appliedToSpans;
    // If the list has any values, then only the spans on the list will get attributes.
    // If the list is empty, then there is no restriction.
    // Used to control attribute inheritance to children spans.
    private transient List<String> inheritanceAllowedSpanIdList;

    @NonNull
    public synchronized Map<AttributeKey<?>, Object> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        return attributes;
    }

    /**
     * Publish an immutable copy of all attributes at once, see {@link #attributes}.
     * Uses the same lock as lazy initialization so a first reader cannot overwrite the replacement.
     */
    public synchronized void replaceAttributes(@NonNull Map<AttributeKey<?>, Object> newAttributes) {
        this.attributes = Collections.unmodifiableMap(new HashMap<>(newAttributes));
    }

    /**
     * Remember a span to which these attributes are applied.
     * @param span the span
     * @return true iff a span did not previously have these attributes applied
     */
    public synchronized boolean isNotYetAppliedToSpan(@NonNull Span span) {
        if (appliedToSpans == null) {
            appliedToSpans = Collections.newSetFromMap(new WeakHashMap<>());
        }
        return appliedToSpans.add(span);
    }

    public void addSpanIdToInheritanceAllowedList(String spanId) {
        if (inheritanceAllowedSpanIdList == null) {
            inheritanceAllowedSpanIdList = new ArrayList<>();
        }
        inheritanceAllowedSpanIdList.add(spanId);
    }

    public boolean inheritanceAllowedSpanIdListIsEmpty() {
        if (inheritanceAllowedSpanIdList == null) {
            return true;
        }
        return inheritanceAllowedSpanIdList.isEmpty();
    }

    public boolean isSpanIdAllowedToInheritAttributes(String spanId) {
        if (inheritanceAllowedSpanIdList == null) {
            return false;
        }
        return inheritanceAllowedSpanIdList.contains(spanId);
    }

    @Override
    public String toString() {
        return "OpenTelemetryAttributesAction{" + "attributes="
                + getAttributes().entrySet().stream()
                        .map(e -> e.getKey().getKey() + "-" + e.getKey().getType() + " - " + e.getValue())
                        .collect(Collectors.joining(", "))
                + '}';
    }
}
