/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import hudson.Extension;
import hudson.tasks.junit.pipeline.JUnitResultsStep;
import io.jenkins.plugins.opentelemetry.job.JUnitAction;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import jenkins.YesNoMaybe;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes.TEST_STAGE_NAME;

/**
 * Customization of spans for {@code junit} step.
 */
@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class JUnitTaskHandler implements StepHandler {

    private OtelTraceService otelTraceService;

    @Override
    public boolean canCreateSpanBuilder(@Nonnull FlowNode flowNode, @Nonnull WorkflowRun run) {
        return flowNode instanceof StepAtomNode && ((StepAtomNode) flowNode).getDescriptor() instanceof JUnitResultsStep.DescriptorImpl;
    }

    @Nonnull
    @Override
    public SpanBuilder createSpanBuilder(@Nonnull FlowNode node, @Nonnull WorkflowRun run, @Nonnull Tracer tracer) {
        return tracer.spanBuilder(node.getDisplayFunctionName());
    }

    @Override
    public void afterSpanCreated(StepAtomNode node, WorkflowRun run) {
        // Gather the testResults argument
        Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(node);
        String testResults = checkNotNull(arguments.get("testResults")).toString();

        JUnitAction junitAction = new JUnitAction(testResults);
        MonitoringAction monitoringAction = run.getAction(MonitoringAction.class);
        monitoringAction
            .getAttributes()
            .forEach((name, value) -> junitAction.getAttributes().put(name, value));

        // Search for the Stage where the junit step run from.
        String stageParent = this.otelTraceService.findStageParent(node);
        if (stageParent != null) {
            junitAction.getAttributes().put(TEST_STAGE_NAME, stageParent);
        }
        run.addAction(junitAction);
    }

    @Inject
    public final void setOpenTelemetryTracerService(@Nonnull OtelTraceService otelTraceService) {
        this.otelTraceService = otelTraceService;
    }
}
