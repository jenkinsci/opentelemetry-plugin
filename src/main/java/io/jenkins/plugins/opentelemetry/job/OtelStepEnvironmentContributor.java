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

    private OtelEnvironmentContributorService otelEnvironmentContributorService;

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
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + "buildEnvironmentFor(flowNode: " + flowNode.getDisplayFunctionName() + ") ");
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
