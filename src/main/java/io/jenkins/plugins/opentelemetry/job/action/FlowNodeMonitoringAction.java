/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.action;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.util.List;

/**
 * Span reference associate with a {@link org.jenkinsci.plugins.workflow.graph.FlowNode}
 */
public class FlowNodeMonitoringAction extends AbstractInvisibleMonitoringAction {

    public FlowNodeMonitoringAction(Span span) {
        super(span);
    }

    public FlowNodeMonitoringAction(Span span, List<Scope> scopes) {
        super(span, scopes);
    }
}
