/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.jenkins;

import hudson.ExtensionList;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.List;

public interface PipelineListener {

    @NonNull
    static List<PipelineListener> all() {
        return ExtensionList.lookup(PipelineListener.class);
    }

    /**
     * Just before the pipeline starts
     */
    default void onStartPipeline(@NonNull FlowNode node, @NonNull WorkflowRun run) {
    }

    /**
     * Just before a `node(...) {...}` block step starts.
     */
    default void onStartNodeStep(@NonNull StepStartNode stepStartNode, @Nullable String nodeLabel, @NonNull WorkflowRun run) {
    }

    /**
     * Just after a `node(...) {...}` block step starts.
     */
    default void onAfterStartNodeStep(@NonNull StepStartNode stepStartNode, @Nullable String nodeLabel, @NonNull WorkflowRun run) {
    }

    /**
     * Just before the end of a `node(...) {...}` block ste.
     */
    default void onEndNodeStep(@NonNull StepEndNode nodeStepEndNode, @NonNull String nodeName, FlowNode nextNode, @NonNull WorkflowRun run) {
    }

    /**
     * Just before a `stage(...) {...}` block step starts
     */
    default void onStartStageStep(@NonNull StepStartNode stepStartNode, @NonNull String stageName, @NonNull WorkflowRun run) {
    }

    /**
     * Just before the end of a `stage(...) {...}` block step
     */
    default void onEndStageStep(@NonNull StepEndNode stageStepEndNode, @NonNull String stageName, FlowNode nextNode, @NonNull WorkflowRun run) {
    }

    /**
     * Just before the `parallel {...}` branch block step starts
     */
    default void onStartParallelStepBranch(@NonNull StepStartNode stepStartNode, @NonNull String branchName, @NonNull WorkflowRun run) {
    }

    /**
     * Just before the `parallel` branch ends
     */
    default void onEndParallelStepBranch(@NonNull StepEndNode stepStepNode, @NonNull String branchName, FlowNode nextNode, @NonNull WorkflowRun run) {
    }

    /**
     * Just before an atomic step starts (e.g. `sh`, `echo`, `sleep`, etc.)
     */
    default void onAtomicStep(@NonNull StepAtomNode node, @NonNull WorkflowRun run) {
    }

    /**
     * Just after an atomic step (e.g. `sh`, `echo`, `sleep`, etc.)
     */
    default void onAfterAtomicStep(@NonNull StepAtomNode stepAtomNode, FlowNode nextNode, @NonNull WorkflowRun run) {
    }

    /**
     * Just after the pipeline ends
     */
    default void onEndPipeline(@NonNull FlowNode node, @NonNull WorkflowRun run) {
    }

    default void onOnOtherBlockStepStartNode(@NonNull StepStartNode node, @NonNull WorkflowRun run) {
    }

    default void onAfterOtherBlockStepStartNode(@NonNull StepStartNode node, @NonNull WorkflowRun run) {
    }

    default void onOtherBlockStepEndNode(@NonNull StepEndNode node, @NonNull WorkflowRun run) {
    }

    default void onAfterOtherBlockStepEndNode(@NonNull StepEndNode node, @NonNull WorkflowRun run) {
    }
}
