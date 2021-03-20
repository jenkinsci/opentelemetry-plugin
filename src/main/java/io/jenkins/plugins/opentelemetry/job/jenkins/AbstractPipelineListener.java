/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.jenkins;

import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class AbstractPipelineListener implements PipelineListener {
    @Override
    public void onStartPipeline(@Nonnull FlowNode node, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onStartNodeStep(@Nonnull StepStartNode stepStartNode, @Nullable String nodeLabel, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onStartStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onAfterStartNodeStep(@Nonnull StepStartNode stepStartNode, @Nullable String nodeLabel, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onEndNodeStep(@Nonnull StepEndNode nodeStepEndNode, @Nonnull String nodeName, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onEndStageStep(@Nonnull StepEndNode stageStepEndNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onAfterAtomicStep(@Nonnull StepAtomNode stepAtomNode, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onStartParallelStepBranch(@Nonnull StepStartNode stepStartNode, @Nonnull String branchName, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onEndParallelStepBranch(@Nonnull StepEndNode stepStepNode, @Nonnull String branchName, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onEndPipeline(@Nonnull FlowNode node, @Nonnull WorkflowRun run) {

    }
}
