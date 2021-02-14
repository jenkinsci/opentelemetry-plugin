/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.TaskListener;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;

import javax.annotation.Nonnull;
import java.util.List;

@Extension
public class OtelEnvironmentContributor extends EnvironmentContributor {

    public static final String OTEL_SPAN_ID = "OTEL_SPAN_ID";
    public static final String OTEL_TRACE_ID = "OTEL_TRACE_ID";

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        MonitoringAction action = run.getAction(MonitoringAction.class);
        if (action == null) {
		    return;
        }

        envs.put(OTEL_SPAN_ID, action.getSpanId());
        envs.put(OTEL_TRACE_ID, action.getTraceId());

        for (MonitoringAction.ObservabilityBackendLink link : action.getLinks()) {
            // Default backend link got an empty environment variable.
            if (link.getEnvVar() != null) {
                envs.put(link.getEnvVar(), link.getUrl());
            }
        }
    }
}
