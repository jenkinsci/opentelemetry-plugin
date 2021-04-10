/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.Extension;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import jenkins.YesNoMaybe;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
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
    public boolean canCreateSpanBuilder(@Nonnull FlowNode flowNode, @Nonnull WorkflowRun run) {
        return super.isGitTask(flowNode, run);
    }

    @Nonnull
    @Override
    public SpanBuilder createSpanBuilder(@Nonnull FlowNode node, @Nonnull WorkflowRun run, @Nonnull Tracer tracer) throws Exception {
        final Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(node);
        final String gitUrl = checkNotNull(arguments.get("url")).toString();
        final String gitBranch = Objects.toString(arguments.get("branch"), null);
        final String credentialsId = (String) arguments.get("credentialsId");
        final String stepFunctionName = node.getDisplayFunctionName();

        return createSpanBuilder(gitUrl, gitBranch, credentialsId, stepFunctionName, tracer, run);
    }

}
