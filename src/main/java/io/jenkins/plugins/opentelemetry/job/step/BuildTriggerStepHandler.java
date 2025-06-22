/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import static com.google.common.base.Preconditions.checkNotNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import java.util.Map;
import jenkins.YesNoMaybe;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.build.BuildTriggerStep;

@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class BuildTriggerStepHandler implements StepHandler {
    @Override
    public boolean canCreateSpanBuilder(@NonNull FlowNode flowNode, @NonNull WorkflowRun run) {
        return flowNode instanceof StepAtomNode
                && ((StepAtomNode) flowNode).getDescriptor() instanceof BuildTriggerStep.DescriptorImpl;
    }

    @NonNull
    @Override
    public SpanBuilder createSpanBuilder(@NonNull FlowNode node, @NonNull WorkflowRun run, @NonNull Tracer tracer) {
        Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(node);
        String job = checkNotNull(arguments.get("job")).toString();
        SpanBuilder spanBuilder = tracer.spanBuilder("build: " + job);
        spanBuilder.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_NAME, job);
        return spanBuilder;
    }
}
