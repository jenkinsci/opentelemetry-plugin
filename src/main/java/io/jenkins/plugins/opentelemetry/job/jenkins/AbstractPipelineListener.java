/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.jenkins;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

public class AbstractPipelineListener implements PipelineListener {
    @Override
    public void onStartPipeline(@NonNull FlowNode node, @NonNull WorkflowRun run) {}

    @Override
    public void onStartNodeStep(
            @NonNull StepStartNode stepStartNode, @Nullable String nodeLabel, @NonNull WorkflowRun run) {}

    @Override
    public void onStartStageStep(
            @NonNull StepStartNode stepStartNode, @NonNull String stageName, @NonNull WorkflowRun run) {}

    @Override
    public void onAfterStartNodeStep(
            @NonNull StepStartNode stepStartNode, @Nullable String nodeLabel, @NonNull WorkflowRun run) {}

    @Override
    public void onEndNodeStep(
            @NonNull StepEndNode nodeStepEndNode,
            @NonNull String nodeName,
            FlowNode nextNode,
            @NonNull WorkflowRun run) {}

    @Override
    public void onEndStageStep(
            @NonNull StepEndNode stageStepEndNode,
            @NonNull String stageName,
            FlowNode nextNode,
            @NonNull WorkflowRun run) {}

    @Override
    public void onStartWithNewSpanStep(@NonNull StepStartNode stepStartNode, @NonNull WorkflowRun run) {}

    @Override
    public void onEndWithNewSpanStep(
            @NonNull StepEndNode nodeStepEndNode, FlowNode nextNode, @NonNull WorkflowRun run) {}

    @Override
    public void onAtomicStep(@NonNull StepAtomNode node, @NonNull WorkflowRun run) {}

    @Override
    public void onAfterAtomicStep(@NonNull StepAtomNode stepAtomNode, FlowNode nextNode, @NonNull WorkflowRun run) {}

    @Override
    public void onStartParallelStepBranch(
            @NonNull StepStartNode stepStartNode, @NonNull String branchName, @NonNull WorkflowRun run) {}

    @Override
    public void onEndParallelStepBranch(
            @NonNull StepEndNode stepStepNode,
            @NonNull String branchName,
            FlowNode nextNode,
            @NonNull WorkflowRun run) {}

    @Override
    public void onEndPipeline(@NonNull FlowNode node, @NonNull WorkflowRun run) {}
}
