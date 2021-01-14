package io.jenkins.plugins.opentelemetry.pipeline.listener;

import hudson.ExtensionList;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
import java.util.List;

public interface PipelineStepListener {

    @Nonnull
    static List<PipelineStepListener> all() {
        return ExtensionList.lookup(PipelineStepListener.class);
    }

    void onStartPipeline(@Nonnull FlowNode node, @Nonnull WorkflowRun run);

    void onBeforeStartStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String stageName, @Nonnull WorkflowRun run);

    void onAfterEndStageStep(@Nonnull StepEndNode stageStepEndNode, @Nonnull String stageName, @Nonnull WorkflowRun run);

    void onBeforeAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run);

    void onAfterAtomicStep(@Nonnull StepAtomNode stepAtomNode, @Nonnull WorkflowRun run);

    void onEndPipeline(@Nonnull FlowNode node, @Nonnull WorkflowRun run);


}
