/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.action;

import io.opentelemetry.api.trace.Span;

/**
 * Span reference associate with a phase of a {@link hudson.model.Run}
 * @see io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes#JENKINS_JOB_SPAN_PHASE_START_NAME
 * @see io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes#JENKINS_JOB_SPAN_PHASE_RUN_NAME
 * @see io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes#JENKINS_JOB_SPAN_PHASE_FINALIZE_NAME
 */
public class RunPhaseMonitoringAction extends AbstractInvisibleMonitoringAction {
    public RunPhaseMonitoringAction(Span span) {
        super(span);
    }
}
