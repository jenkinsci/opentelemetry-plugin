/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.action;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.util.Collections;
import java.util.List;

public abstract class AbstractInvisibleMonitoringAction extends AbstractMonitoringAction {

    public AbstractInvisibleMonitoringAction(Span span) {
        super(span, Collections.emptyList());
    }

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
