/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import hudson.Extension;
import hudson.tasks.junit.pipeline.JUnitResultsStep;
import io.jenkins.plugins.opentelemetry.job.JunitAction;
import io.opentelemetry.api.common.AttributeKey;
import jenkins.YesNoMaybe;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * Customization of spans for {@code junit} step.
 */
@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class JunitTaskHandler extends DefaultStepHandler {
    @Override
    public boolean canCreateSpanBuilder(@Nonnull FlowNode flowNode, @Nonnull WorkflowRun run) {
        return flowNode instanceof StepAtomNode && ((StepAtomNode) flowNode).getDescriptor() instanceof JUnitResultsStep.DescriptorImpl;
    }

    @Override
    public void afterSpanCreated(StepAtomNode node, WorkflowRun run) {
        JunitAction junitAction = new JunitAction();
        Map<String, String> attributes = new HashMap<>();
        attributes.put("foo", "bar");
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            junitAction.getAttributes().put(AttributeKey.stringKey(attribute.getKey()), attribute.getValue());
        }
        run.addAction(junitAction);
    }
}
