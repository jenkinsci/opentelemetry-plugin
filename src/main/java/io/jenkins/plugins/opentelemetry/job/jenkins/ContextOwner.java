/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.jenkins;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

/**
 * Marks each context that this package makes current on a CPS VM thread with its owner, so that the owner can tell
 * whether its context is still the innermost one on the thread before it changes it. The identity of the context can't
 * tell: with the API bridge of the OpenTelemetry Java agent, each {@link Context#current()} returns a new object. A
 * value of a key of this plugin goes through the bridge, and so do the span contexts.
 */
final class ContextOwner {
    private static final ContextKey<Object> OWNER = ContextKey.named("jenkins-opentelemetry-cps-vm-context-owner");

    private ContextOwner() {}

    /** {@code parent} with {@code span} current, owned by {@code owner}. */
    @NonNull
    static Context with(@NonNull Context parent, @NonNull Span span, @NonNull Object owner) {
        return parent.with(span).with(OWNER, owner);
    }

    /**
     * Whether the current context of this thread is the one that {@code owner} made current with {@code span}, and not
     * a context opened after it: one of another owner, or one with another span.
     */
    static boolean isCurrent(@NonNull Object owner, @NonNull Span span) {
        Context current = Context.current();
        return current.get(OWNER) == owner
                && Span.fromContext(current).getSpanContext().equals(span.getSpanContext());
    }

    /** Whether {@code a} and {@code b} are the same span, also when the API bridge wraps them in different objects. */
    static boolean sameSpan(@NonNull Span a, @NonNull Span b) {
        return a.getSpanContext().equals(b.getSpanContext());
    }
}
