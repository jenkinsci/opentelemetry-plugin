/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import com.google.common.base.Strings;
import hudson.Extension;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import jenkins.YesNoMaybe;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Objects;

/**
 * Customization of spans for shell step: ({@code sh}, {@code cmd}, and {@code powershell}).
 */
@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class  DurableTaskHandler implements StepHandler {
    @Override
    public boolean canCreateSpanBuilder(@NonNull FlowNode flowNode, @NonNull WorkflowRun run) {
        return flowNode instanceof StepAtomNode && ((StepAtomNode) flowNode).getDescriptor() instanceof DurableTaskStep.DurableTaskStepDescriptor;
    }

    @NonNull
    @Override
    public SpanBuilder createSpanBuilder(@NonNull FlowNode node, @NonNull WorkflowRun run, @NonNull Tracer tracer) {
        final Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(node);
        final String displayFunctionName = node.getDisplayFunctionName();
        final String label = Objects.toString(arguments.get("label"), null);
        String spanName = Strings.isNullOrEmpty(label) ? displayFunctionName : label;
        return tracer.spanBuilder(spanName);
    }
}
