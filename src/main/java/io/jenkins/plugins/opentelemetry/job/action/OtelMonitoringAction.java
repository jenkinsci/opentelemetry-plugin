/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.action;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Action;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.trace.ReadableSpan;

import java.util.Map;

/**
 * Action to decorate {@link hudson.model.Job} steps to hold references to {@link Span}s
 */
public interface OtelMonitoringAction extends Action {

    Map<String, String> getW3cTraceContext();

    @CheckForNull
    Span getSpan();

    void purgeSpanAndCloseAssociatedScopes();

    void closeAndPurgeAssociatedScope();

    /**
     * @return {@code true} if the associated {@link Span} has ended
     * @see ReadableSpan#hasEnded()
     */
    boolean hasEnded();
}
