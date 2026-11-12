/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.Span;
import java.util.List;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * Attribute of the span of a {@code withEnv} step: the number of overrides. The names and the values can be or
 * contain secrets, so they are never recorded.
 */
@Extension
public class WithEnvBlockStepHandler implements BlockStepHandler {

    @Override
    public boolean canHandle(@NonNull String functionName) {
        return "withEnv".equals(functionName);
    }

    @Override
    public void onBlockStepStart(
            @NonNull Span blockSpan, @NonNull StepStartNode outerStartNode, @NonNull WorkflowRun run) {
        if (ArgumentsAction.getFilteredArguments(outerStartNode).get("overrides") instanceof List<?> overrides) {
            blockSpan.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ENV_COUNT, (long) overrides.size());
        }
    }
}
