/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.opentelemetry.api.trace.Span;
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
        Span span = Span.current();
        // If there is no active span on the thread, it falls back to the run's root span
        if (!span.getSpanContext().isValid()) {
            span = otelTraceService.getSpan(run);
        }
        otelEnvironmentContributorService.addEnvironmentVariables(run, envs, span);
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
