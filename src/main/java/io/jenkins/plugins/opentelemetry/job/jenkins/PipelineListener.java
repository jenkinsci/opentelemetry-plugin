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

import javax.annotation.Nonnull;
import java.util.List;

public interface PipelineListener {

    @Nonnull
    static List<PipelineListener> all() {
        return ExtensionList.lookup(PipelineListener.class);
    }

    /**
     * Just before the pipeline starts
     */
    void onStartPipeline(@Nonnull FlowNode node, @Nonnull WorkflowRun run);

    /**
     * Just before the `node`step starts
     */
    void onStartNodeStep(@Nonnull StepStartNode stepStartNode, @Nonnull String nodeName, @Nonnull WorkflowRun run);

    /**
     * Just after the `node` step ends
     */
    void onEndNodeStep(@Nonnull StepEndNode nodeStepEndNode, @Nonnull String nodeName, @Nonnull WorkflowRun run);

    /**
     * Just before the `stage`step starts
     */
    void onStartStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String stageName, @Nonnull WorkflowRun run);

    /**
     * Just after the `stage` step ends
     */
    void onEndStageStep(@Nonnull StepEndNode stageStepEndNode, @Nonnull String stageName, @Nonnull WorkflowRun run);

    /**
     * Just before the `parallel` branch starts
     */
    void onStartParallelStepBranch(@Nonnull StepStartNode stepStartNode, @Nonnull String branchName, @Nonnull WorkflowRun run);

    /**
     * Just before the `parallel` branch ends
     */
    void onEndParallelStepBranch(@Nonnull StepEndNode stepStepNode, @Nonnull String branchName, @Nonnull WorkflowRun run);

    /**
     * Just before the atomic step starts
     */
    void onAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run);

    /**
     * Just after the atomic step
     */
    void onAfterAtomicStep(@Nonnull StepAtomNode stepAtomNode, @Nonnull WorkflowRun run);

    /**
     * Just after the pipeline ends
     */
    void onEndPipeline(@Nonnull FlowNode node, @Nonnull WorkflowRun run);

}
