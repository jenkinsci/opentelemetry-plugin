/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.action;

import io.opentelemetry.api.trace.Span;

/**
 * Span reference associate with a {@link hudson.tasks.BuildStep}
 */
public class BuildStepMonitoringAction extends AbstractInvisibleMonitoringAction {
    public BuildStepMonitoringAction(Span span) {
        super(span);
    }
}
