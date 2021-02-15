/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepEnvironmentContributor;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class OtelStepEnvironmentContributor extends StepEnvironmentContributor {

    private final static Logger LOGGER = Logger.getLogger(OtelStepEnvironmentContributor.class.getName());

    public static final String SPAN_ID = "SPAN_ID";
    public static final String TRACE_ID = "TRACE_ID";

    private OtelTraceService otelTraceService;

    @Override
    public void buildEnvironmentFor(@Nonnull StepContext stepContext, @Nonnull EnvVars envs, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        super.buildEnvironmentFor(stepContext, envs, listener);
        Run run = stepContext.get(Run.class);
        FlowNode flowNode = stepContext.get(FlowNode.class);

        Span span;
        if (flowNode == null) {
            LOGGER.log(Level.WARNING, () -> run.getFullDisplayName() + "buildEnvironmentFor() NO flowNode found for context " + stepContext);
            span = otelTraceService.getSpan(run);
        } else {
            LOGGER.log(Level.INFO, () -> run.getFullDisplayName() + "buildEnvironmentFor(flowNode: " + flowNode.getDisplayFunctionName() + ") ");
            span = otelTraceService.getSpan(run, flowNode);
        }

        if (span == null) {
            LOGGER.log(Level.WARNING, () -> run.getFullDisplayName() + "buildEnvironmentFor() NO span found for context " + stepContext);
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
