/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import hudson.Extension;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import jenkins.YesNoMaybe;
import jenkins.plugins.git.GitStep;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Customization of the span for {@code git} steps.
 */
@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class GitStepHandler extends AbstractGitStepHandler {

    private final static Logger LOGGER = Logger.getLogger(GitStepHandler.class.getName());

    @Override
    public boolean canCreateSpanBuilder(@NonNull FlowNode flowNode, @NonNull WorkflowRun run) {
        return flowNode instanceof StepAtomNode && ((StepAtomNode) flowNode).getDescriptor() instanceof GitStep.DescriptorImpl;
    }

    @NonNull
    @Override
    public SpanBuilder createSpanBuilder(@NonNull FlowNode node, @NonNull WorkflowRun run, @NonNull Tracer tracer) {
        final Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(node);
        final String gitUrl = checkNotNull(arguments.get("url")).toString();
        final String gitBranch = Objects.toString(arguments.get("branch"), null);
        final String credentialsId = (String) arguments.get("credentialsId");
        final String stepFunctionName = node.getDisplayFunctionName();

        return createSpanBuilder(gitUrl, gitBranch, credentialsId, stepFunctionName, tracer, run);
    }

}