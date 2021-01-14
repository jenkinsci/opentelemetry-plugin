package io.jenkins.plugins.opentelemetry.pipeline.listener;

import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;

public interface PipelineAdvancedStepListener {

    void onAfterAtomicStep(StepAtomNode stepAtomNode, WorkflowRun run);

    void onAfterStartStageStep(StepStartNode previousNode, String stageName, WorkflowRun run);

    void onAfterPreStartStageStep(StepStartNode previousNode, String stageName, WorkflowRun run);

    void onEndPipeline(FlowNode node, WorkflowRun run);

    void onStartPipeline(FlowNode node, WorkflowRun run);

    void onBeforeEndPreStageStep(StepEndNode stepEndNode, String stageName, WorkflowRun run);

    void onAfterEndStageStep(@Nonnull StepEndNode stageStepEndNode, @Nonnull String
            stageName, @Nonnull WorkflowRun run);

    void onBeforeEndStageStep(@Nonnull StepEndNode stepEndNode, String stageName, @Nonnull WorkflowRun
            run);


    void onBeforeAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run);


    void onBeforeStartStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String
            stageName, @Nonnull WorkflowRun run);

    void onBeforeStartPreStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String
            stageName, @Nonnull WorkflowRun run);

    void onAfterEndPreStageStep(@Nonnull StepEndNode stepEndNode, @Nonnull String
            stageName, @Nonnull WorkflowRun run);

}
