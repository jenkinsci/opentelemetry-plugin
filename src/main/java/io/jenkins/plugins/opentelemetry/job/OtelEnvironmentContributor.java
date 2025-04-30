/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

/**
 * Inject OpenTelemetry environment variables in shell steps: {@code TRACEPARENT}, {@code OTEL_EXPORTER_OTLP_ENDPOINT}...
 */
@Extension
public class OtelEnvironmentContributor extends EnvironmentContributor {

    private OtelEnvironmentContributorService otelEnvironmentContributorService;

    private OtelTraceService otelTraceService;

    @Override
    public void buildEnvironmentFor(@NonNull Run run, @NonNull EnvVars envs, @NonNull TaskListener listener) {
        otelEnvironmentContributorService.addEnvironmentVariables(run, envs, otelTraceService.getSpan(run));
    }

    @Inject
    public void setOtelTraceService(OtelTraceService otelTraceService) {
        this.otelTraceService = otelTraceService;
    }

    @Inject
    public void setEnvironmentContributorService(OtelEnvironmentContributorService otelEnvironmentContributorService) {
        this.otelEnvironmentContributorService = otelEnvironmentContributorService;
    }
}
