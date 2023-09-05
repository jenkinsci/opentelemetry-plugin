/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.action;

import io.opentelemetry.api.trace.Span;

public class FlowNodeMonitoringAction extends AbstractInvisibleMonitoringAction {
    public FlowNodeMonitoringAction(Span span) {
        super(span);
    }
}
