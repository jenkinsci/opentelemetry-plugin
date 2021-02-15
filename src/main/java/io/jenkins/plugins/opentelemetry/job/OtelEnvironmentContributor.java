/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;

import javax.annotation.Nonnull;

@Extension
public class OtelEnvironmentContributor extends EnvironmentContributor {

    public static final String TRACEPARENT_VERSION_HEADER = "00";
    public static final String TRACEPARENT_SAMPLED_FLAG_HEADER = "01";
    public static final String OTEL_SPAN_ID = "SPAN_ID";
    public static final String OTEL_PARENT_ID = "PARENT_ID";
    public static final String OTEL_TRACE_ID = "TRACE_ID";
    public static final String OTEL_TRACE_PARENT = "TRACEPARENT";

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        MonitoringAction action = run.getAction(MonitoringAction.class);
        if (action == null) {
            return;
        }

        envs.put(OTEL_SPAN_ID, action.getSpanId());
        envs.put(OTEL_PARENT_ID, action.getSpanId());
        envs.put(OTEL_TRACE_ID, action.getTraceId());
        // See https://www.w3.org/TR/trace-context/#traceparent-header
        envs.put(OTEL_TRACE_PARENT, TRACEPARENT_VERSION_HEADER + "-" + action.getTraceId() + "-" + action.getSpanId() + "-" + TRACEPARENT_SAMPLED_FLAG_HEADER);

        for (MonitoringAction.ObservabilityBackendLink link : action.getLinks()) {
            // Default backend link got an empty environment variable.
            if (link.getEnvVar() != null) {
                envs.put(link.getEnvVar(), link.getUrl());
            }
        }
    }
}
