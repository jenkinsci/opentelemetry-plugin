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
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class OtelEnvironmentContributor extends EnvironmentContributor {
    private final static Logger LOGGER = Logger.getLogger(OtelEnvironmentContributor.class.getName());

    private OtelTraceService otelTraceService;

    public static final String SPAN_ID = "SPAN_ID";
    public static final String TRACE_ID = "TRACE_ID";

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        if (run instanceof WorkflowRun) {
            // skip, will be handle by OtelStepEnvironmentContributor
            LOGGER.log(Level.FINER, () -> run.getFullDisplayName() + "buildEnvironmentFor(envs: " + envs + ") : skip, will be handled by OtelStepEnvironmentContributor");
            return;
        }
        Span span = otelTraceService.getSpan(run);
        if (span == null) {
            LOGGER.log(Level.WARNING, () -> run.getFullDisplayName() + "buildEnvironmentFor() NO span found");
            return;
        }
        try (Scope ignored = span.makeCurrent()) {
            String spanId = span.getSpanContext().getSpanId();
            String traceId = span.getSpanContext().getTraceId();
            envs.put(TRACE_ID, traceId);
            envs.put(SPAN_ID, spanId);
            TextMapPropagator.Setter<EnvVars> setter = (carrier, key, value) -> carrier.put(key.toUpperCase(), value);
            W3CTraceContextPropagator.getInstance().inject(Context.current(), envs, setter);
        }

        // FIXME MonitoringAction may be positioned on a wrong spanId (in case of parallel steps). We need another mechanism if we want to output the visualisation URLs
        MonitoringAction action = run.getAction(MonitoringAction.class);
        if (action == null) {
            // unexpected
        } else {
            for (MonitoringAction.ObservabilityBackendLink link : action.getLinks()) {
                // Default backend link got an empty environment variable.
                if (link.getEnvironmentVariableName() != null) {
                    envs.put(link.getEnvironmentVariableName(), link.getUrl());
                }
            }
        }

    }


    @Inject
    public void setOtelTraceService(OtelTraceService otelTraceService) {
        this.otelTraceService = otelTraceService;
    }
}
