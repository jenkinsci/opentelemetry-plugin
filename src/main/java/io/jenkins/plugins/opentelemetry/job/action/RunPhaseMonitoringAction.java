/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.action;

import hudson.model.Action;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.Span;

/**
 * Span reference associated with a phase of a {@link hudson.model.Run} as a {@link hudson.model.Run#getActions(Class)}
 * @see hudson.model.Run#addAction(Action) 
 * @see ExtendedJenkinsAttributes#JENKINS_JOB_SPAN_PHASE_START_NAME
 * @see ExtendedJenkinsAttributes#JENKINS_JOB_SPAN_PHASE_RUN_NAME
 * @see ExtendedJenkinsAttributes#JENKINS_JOB_SPAN_PHASE_FINALIZE_NAME
 */
public class RunPhaseMonitoringAction extends AbstractInvisibleMonitoringAction {
    public RunPhaseMonitoringAction(Span span) {
        super(span);
    }
}
