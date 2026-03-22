/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.action;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.util.Collections;
import java.util.List;

/**
 * Base monitoring action that remains hidden from Jenkins side panels and action listings.
 */
public abstract class AbstractInvisibleMonitoringAction extends AbstractMonitoringAction {

    /**
     * Creates an invisible monitoring action with no extra scopes to close.
     *
     * @param span span associated with this action
     */
    public AbstractInvisibleMonitoringAction(Span span) {
        super(span, Collections.emptyList());
    }

    /**
     * Creates an invisible monitoring action with the provided scope stack.
     *
     * @param span span associated with this action
     * @param scopes scopes that should be tracked and closed with this action
     */
    public AbstractInvisibleMonitoringAction(Span span, List<Scope> scopes) {
        super(span, scopes);
    }

    @Override
    public final String getIconFileName() {
        return null;
    }

    @Override
    public final String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }
}
