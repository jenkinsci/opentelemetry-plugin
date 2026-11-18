/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.Span;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * Attributes of the span of a {@code withCredentials} step: the kinds of its bindings and, if the setting is on, the
 * credential IDs. Variable names, user names and secrets are never recorded. Read from the sanitized arguments of the
 * node, so this needs no dependency on credentials-binding.
 */
@Extension
public class WithCredentialsBlockStepHandler implements BlockStepHandler {

    @Override
    public boolean canHandle(@NonNull String functionName) {
        return "withCredentials".equals(functionName);
    }

    @Override
    public void onBlockStepStart(
            @NonNull Span blockSpan, @NonNull StepStartNode outerStartNode, @NonNull WorkflowRun run) {
        if (!(ArgumentsAction.getFilteredArguments(outerStartNode).get("bindings") instanceof List<?> bindings)) {
            return;
        }
        List<String> types = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (Object binding : bindings) {
            if (binding instanceof UninstantiatedDescribable describable) {
                if (describable.getSymbol() != null) {
                    types.add(describable.getSymbol());
                }
                Object id = describable.getArguments().get("credentialsId");
                if (id instanceof String credentialsId) {
                    ids.add(credentialsId);
                }
            }
        }
        if (!types.isEmpty()) {
            blockSpan.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_CREDENTIALS_BINDING_TYPES, types);
        }
        if (!ids.isEmpty() && JenkinsOpenTelemetryPluginConfiguration.get().isIncludeCredentialIds()) {
            blockSpan.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_CREDENTIALS_IDS, ids);
        }
    }
}
