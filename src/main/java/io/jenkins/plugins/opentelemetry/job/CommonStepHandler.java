/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import hudson.Extension;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import jenkins.YesNoMaybe;
import jenkins.plugins.git.GitStep;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.BranchJobProperty;
import org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep;
import org.jenkinsci.plugins.workflow.steps.scm.GenericSCMStep;

import javax.annotation.Nonnull;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Customization of spans for dynamically add attributes to the steps.
 */
@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class CommonStepHandler implements StepHandler {
    private final static Logger LOGGER = Logger.getLogger(CommonStepHandler.class.getName());

    @Override
    public boolean canCreateSpanBuilder(@Nonnull FlowNode flowNode, @Nonnull WorkflowRun run) {
        if (isDurableTask(flowNode, run) || isGitTask(flowNode, run) || isGitCheckoutTask(flowNode, run)) {
            return false;
        }
        return flowNode instanceof StepAtomNode;
    }

    @Nonnull
    @Override
    public SpanBuilder createSpanBuilder(@Nonnull FlowNode node, @Nonnull  WorkflowRun run, @Nonnull Tracer tracer) throws Exception {
        final String displayFunctionName = node.getDisplayFunctionName();
        return this.createSpanBuilderFromApmDetails(node, tracer.spanBuilder(displayFunctionName));
    }

    /**
     * Visible for testing.
     */
    @VisibleForTesting
    protected SpanBuilder createSpanBuilderFromApmDetails(@Nonnull FlowNode node, @Nonnull SpanBuilder spanBuilder) throws URISyntaxException {
        final Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(node);
        Map<String, String> apmAttributes = (Map<String, String>) arguments.get("apm");
        if(apmAttributes != null && apmAttributes.size() > 0) {
            LOGGER.log(Level.INFO, () -> "Create apm " + apmAttributes.toString());
            // TODO: validate the attributes and only support the ones that the OTEL provides.
            apmAttributes.forEach(spanBuilder::setAttribute);
        }
        return spanBuilder;
    }

    protected boolean isDurableTask(@Nonnull FlowNode flowNode, @Nonnull WorkflowRun run) {
        return flowNode instanceof StepAtomNode && ((StepAtomNode) flowNode).getDescriptor() instanceof DurableTaskStep.DurableTaskStepDescriptor;
    }

    protected boolean isGitTask(@Nonnull FlowNode flowNode, @Nonnull WorkflowRun run) {
        return flowNode instanceof StepAtomNode && ((StepAtomNode) flowNode).getDescriptor() instanceof GitStep.DescriptorImpl;
    }

    protected boolean isGitCheckoutTask(@Nonnull FlowNode flowNode, @Nonnull WorkflowRun run) {
        if (!(flowNode instanceof StepAtomNode)) {
            return false;
        }
        StepAtomNode stepAtomNode = (StepAtomNode) flowNode;
        if (!(stepAtomNode.getDescriptor() instanceof GenericSCMStep.DescriptorImpl)) {
            return false;
        }

        final WorkflowJob pipeline = run.getParent();
        final BranchJobProperty branchJobProperty = pipeline.getProperty(BranchJobProperty.class);
        if (branchJobProperty == null) {
            // FIXME implement generic `checkout ...` step
            final Map<String, Object> rootArguments = ArgumentsAction.getFilteredArguments(flowNode);
            final Map<String, ?> scm = (Map<String, ?>) rootArguments.get("scm");
            if (scm == null) {
                return false;
            }
            final Object clazz = scm.get("$class");
            if (!(Objects.equal(GitSCM.class.getSimpleName(), clazz))) {
                return false;
            }
            return true;
        } else {
            // MultiBranch Pipeline using Git
            final SCM scm = branchJobProperty.getBranch().getScm();
            if (scm instanceof GitSCM) {
                return true;
            }
        }

        return false;
    }
}
