/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.jenkins;

import edu.umd.cs.findbugs.annotations.CheckForNull;
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
     * Just after a block step (a step with a body, other than the ones that have their own methods here: `node`,
     * `stage`, `parallel` branch and `withNewSpan`) starts, on its outer start node. The body has not started.
     */
    default void onStartBlockStep(@NonNull StepStartNode stepStartNode, @NonNull WorkflowRun run) {}

    /**
     * When the next node arrives after the outer end node of a block step, so that the end node's error is known.
     * If the body never started, the outer end node follows the outer start node. A few steps have no end node in
     * that case, see {@link #onEndBlockStepWithoutEndNode(StepStartNode, FlowNode, WorkflowRun)}.
     */
    default void onEndBlockStep(
            @NonNull StepEndNode stepEndNode, @CheckForNull FlowNode nextNode, @NonNull WorkflowRun run) {}

    /**
     * When the node after the outer start node of a block step is neither its body start node nor its own end node
     * (for example the next step, or the end of an enclosing block): the step completed without a body and without an
     * end node (for example a {@code lock} skipped with {@code skipIfLocked}), so nothing else ends its span.
     */
    default void onEndBlockStepWithoutEndNode(
            @NonNull StepStartNode stepStartNode, @NonNull FlowNode nextNode, @NonNull WorkflowRun run) {}

    /**
     * Just after the body of a block step starts, on the body start node. Called for each invocation of the body
     * (a `retry` invokes it once per attempt).
     */
    default void onStartBlockBody(@NonNull StepStartNode bodyStartNode, @NonNull WorkflowRun run) {}

    /**
     * When the next node arrives after the body end node of a block step, so that the end node's error is known.
     */
    default void onEndBlockBody(
            @NonNull StepEndNode bodyEndNode, @CheckForNull FlowNode nextNode, @NonNull WorkflowRun run) {}

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
