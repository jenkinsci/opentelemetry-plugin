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

public class OpenTelemetryEnvironmentContributor extends EnvironmentContributor {
    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        MonitoringAction action = run.getAction(MonitoringAction.class);
        if (action == null) {
			return;
		}

        envs.put("OT_SPAN_ID", action.getSpanId());
        envs.put("OT_TRACE_ID", action.getTraceId());
    }
}
