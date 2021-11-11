/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import jenkins.YesNoMaybe;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.build.BuildTriggerStep;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class BuildTriggerStepHandler implements StepHandler {
    private final static Logger LOGGER = Logger.getLogger(BuildTriggerStepHandler.class.getName());
    @Override
    public boolean canCreateSpanBuilder(@Nonnull FlowNode flowNode, @Nonnull WorkflowRun run) {
        return flowNode instanceof StepAtomNode && ((StepAtomNode) flowNode).getDescriptor() instanceof BuildTriggerStep.DescriptorImpl;
    }

    @Nonnull
    @Override
    public SpanBuilder createSpanBuilder(@Nonnull FlowNode node, @Nonnull WorkflowRun run, @Nonnull Tracer tracer) {
        Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(node);
        String job = checkNotNull(arguments.get("job")).toString();
        SpanBuilder spanBuilder = tracer.spanBuilder("build: " + job);
        spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_NAME, job);
        return spanBuilder;
    }

    @Override
    public void enrichContext(StepAtomNode node, WorkflowRun run) {
        Map<String, String> context = new HashMap<>();
        MonitoringAction monitoringAction = run.getAction(MonitoringAction.class);
        W3CTraceContextPropagator.getInstance().inject(Context.current(), context, (carrier, key, value) -> carrier.put(key, value));
        monitoringAction.addContext(node, context);
    }
}
