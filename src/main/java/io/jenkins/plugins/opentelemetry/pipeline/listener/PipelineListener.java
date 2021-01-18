package io.jenkins.plugins.opentelemetry.pipeline.listener;

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
