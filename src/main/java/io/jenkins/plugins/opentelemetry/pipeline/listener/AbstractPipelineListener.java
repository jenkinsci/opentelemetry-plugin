package io.jenkins.plugins.opentelemetry.pipeline.listener;

import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;

public class AbstractPipelineListener implements PipelineStepListener{
    @Override
    public void onStartPipeline(@Nonnull FlowNode node, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onBeforeStartStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onAfterEndStageStep(@Nonnull StepEndNode stageStepEndNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onBeforeAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onAfterAtomicStep(@Nonnull StepAtomNode stepAtomNode, @Nonnull WorkflowRun run) {

    }

    @Override
    public void onEndPipeline(@Nonnull FlowNode node, @Nonnull WorkflowRun run) {

    }
}
