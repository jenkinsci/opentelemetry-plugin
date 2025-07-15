/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.jenkins;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import java.util.List;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

public interface PipelineListener {

    @NonNull
    static List<PipelineListener> all() {
        return ExtensionList.lookup(PipelineListener.class);
    }

    /**
     * Just before the pipeline starts
     */
    void onStartPipeline(@NonNull FlowNode node, @NonNull WorkflowRun run);

    /**
     * Just before the `node` step starts.
     */
    void onStartNodeStep(@NonNull StepStartNode stepStartNode, @Nullable String nodeLabel, @NonNull WorkflowRun run);

    /**
     * Just after the `node` step starts.
     */
    void onAfterStartNodeStep(
            @NonNull StepStartNode stepStartNode, @Nullable String nodeLabel, @NonNull WorkflowRun run);

    /**
     * Just after the `node` step ends
     */
    void onEndNodeStep(
            @NonNull StepEndNode nodeStepEndNode,
            @NonNull String nodeName,
            FlowNode nextNode,
            @NonNull WorkflowRun run);

    /**
     * Just before the `stage`step starts
     */
    void onStartStageStep(@NonNull StepStartNode stepStartNode, @NonNull String stageName, @NonNull WorkflowRun run);

    /**
     * Just after the `stage` step ends
     */
    void onEndStageStep(
            @NonNull StepEndNode stageStepEndNode,
            @NonNull String stageName,
            FlowNode nextNode,
            @NonNull WorkflowRun run);

    /**
     * Just before the `parallel` branch starts
     */
    void onStartParallelStepBranch(
            @NonNull StepStartNode stepStartNode, @NonNull String branchName, @NonNull WorkflowRun run);

    /**
     * Just before the `parallel` branch ends
     */
    void onEndParallelStepBranch(
            @NonNull StepEndNode stepStepNode, @NonNull String branchName, FlowNode nextNode, @NonNull WorkflowRun run);

    /**
     * Just before the `withNewSpan` step starts
     */
    void onStartWithNewSpanStep(@NonNull StepStartNode stepStartNode, @NonNull WorkflowRun run);

    /**
     * Just before the `withNewSpan` step ends
     */
    void onEndWithNewSpanStep(@NonNull StepEndNode nodeStepEndNode, FlowNode nextNode, @NonNull WorkflowRun run);

    /**
     * Just before the atomic step starts
     */
    void onAtomicStep(@NonNull StepAtomNode node, @NonNull WorkflowRun run);

    /**
     * Just after the atomic step
     */
    void onAfterAtomicStep(@NonNull StepAtomNode stepAtomNode, FlowNode nextNode, @NonNull WorkflowRun run);

    /**
     * Just after the pipeline ends
     */
    void onEndPipeline(@NonNull FlowNode node, @NonNull WorkflowRun run);
}
