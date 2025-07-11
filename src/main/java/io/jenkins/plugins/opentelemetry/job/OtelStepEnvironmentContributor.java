/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.opentelemetry.api.trace.Span;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepEnvironmentContributor;

/**
 * Inject OpenTelemetry environment variables in shell steps: {@code TRACEPARENT}, {@code OTEL_EXPORTER_OTLP_ENDPOINT}...
 */
@Extension
public class OtelStepEnvironmentContributor extends StepEnvironmentContributor {

    private static final Logger LOGGER = Logger.getLogger(OtelStepEnvironmentContributor.class.getName());

    private OtelEnvironmentContributorService otelEnvironmentContributorService;

    private OtelTraceService otelTraceService;

    @Override
    public void buildEnvironmentFor(
            @NonNull StepContext stepContext, @NonNull EnvVars envs, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        super.buildEnvironmentFor(stepContext, envs, listener);
        Run run = Objects.requireNonNull(stepContext.get(Run.class));
        FlowNode flowNode = stepContext.get(FlowNode.class);

        Span span;
        if (flowNode == null) {
            LOGGER.log(
                    Level.WARNING,
                    () -> run.getFullDisplayName() + "buildEnvironmentFor() NO flowNode found for context "
                            + stepContext);
            span = otelTraceService.getSpan(run);
        } else {
            LOGGER.log(
                    Level.FINE,
                    () -> run.getFullDisplayName() + "buildEnvironmentFor(flowNode: "
                            + flowNode.getDisplayFunctionName() + ") ");
            span = otelTraceService.getSpan(run, flowNode);
        }

        otelEnvironmentContributorService.addEnvironmentVariables(run, envs, span);
    }

    @Inject
    public void setEnvironmentContributorService(OtelEnvironmentContributorService otelEnvironmentContributorService) {
        this.otelEnvironmentContributorService = otelEnvironmentContributorService;
    }

    @Inject
    public void setOtelTraceService(OtelTraceService otelTraceService) {
        this.otelTraceService = otelTraceService;
    }
}
