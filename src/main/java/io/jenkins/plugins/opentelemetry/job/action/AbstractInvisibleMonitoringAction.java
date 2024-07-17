/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.action;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

public abstract class AbstractInvisibleMonitoringAction extends AbstractMonitoringAction {

    public AbstractInvisibleMonitoringAction(Span span) {
        super(span, null);
    }

    public AbstractInvisibleMonitoringAction(Span span, Scope scope) {
        super(span, scope);
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
