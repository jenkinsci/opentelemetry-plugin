package io.jenkins.plugins.opentelemetry.pipeline.listener;

import com.google.common.collect.Iterables;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.pipeline.PipelineNodeUtil;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adapter to simplify the implementation of pipeline {@link org.jenkinsci.plugins.workflow.steps.Step} listeners.
 */
@Extension
public class GraphListenerAdapterToPipelineStepListener implements GraphListener, GraphListener.Synchronous {
    private final static Logger LOGGER = Logger.getLogger(GraphListenerAdapterToPipelineStepListener.class.getName());

    @Override
    public final void onNewHead(FlowNode node) {
        WorkflowRun run = PipelineNodeUtil.getWorkflowRun(node);
        log(Level.FINE, () -> "_onNewHead" + node.getDisplayFunctionName() + " / " + PipelineNodeUtil.getDisplayName(node) + ", " + ", node.parent: " + Iterables.getFirst(node.getParents(), null));

        FlowNode previousNode = PipelineNodeUtil.getPreviousNode(node);
        if (isBeforeEndStageStep(previousNode)) {
            String stageName = PipelineNodeUtil.getDisplayName(((StepEndNode) previousNode).getStartNode());
            fireOnAfterEndStageStep((StepEndNode) previousNode, stageName, run);
        } else if (previousNode instanceof StepAtomNode) {
            StepAtomNode stepAtomNode = (StepAtomNode) previousNode;
            fireOnAfterAtomicStep(stepAtomNode, run);
        } else {
            log(Level.FINE, () -> "ignore previous node " + previousNode);
        }

        if (node instanceof FlowStartNode) {
            fireOnStartPipeline((FlowStartNode) node, run);
        } else if (node instanceof FlowEndNode) {
            fireOnEndPipeline((FlowEndNode) node, run);
        } else if (PipelineNodeUtil.isStage(node)) {
            String stageName = PipelineNodeUtil.getDisplayName(node);
            fireOnBeforeStartStageStep((StepStartNode) node, stageName, run);
        } else if (node instanceof StepAtomNode) {
            fireOnBeforeAtomicStep((StepAtomNode) node, run);
        } else {
            log(() -> "on_" + node.getDisplayName() + " - " + node.getDisplayFunctionName());
        }
    }

    public void  fireOnAfterAtomicStep(StepAtomNode stepAtomNode, WorkflowRun run) {
        for(PipelineStepListener pipelineStepListener: PipelineStepListener.all()) {
            log(() -> "onAfterAtomicStep(" + stepAtomNode.getDisplayName() + "): " + pipelineStepListener.toString());
            try {
                pipelineStepListener.onAfterAtomicStep(stepAtomNode, run);
            } catch(RuntimeException e) {
                LOGGER.log(Level.WARNING, ()-> "Exception invoking `onAfterAtomicStep` on " + pipelineStepListener);
            }
        }
    }

    public void fireOnEndPipeline(FlowEndNode node, WorkflowRun run) {
        for(PipelineStepListener pipelineStepListener: PipelineStepListener.all()) {
            log(() -> "onEndPipeline(" + node.getDisplayName() + "): " + pipelineStepListener.toString());
            try {
                pipelineStepListener.onEndPipeline(node, run);
            } catch(RuntimeException e) {
                LOGGER.log(Level.WARNING, ()-> "Exception invoking `onEndPipeline` on " + pipelineStepListener);
            }
        }
    }

    public void fireOnStartPipeline(FlowStartNode node, WorkflowRun run) {
        for(PipelineStepListener pipelineStepListener: PipelineStepListener.all()) {
            log(() -> "onStartPipeline(" + node.getDisplayName() + ") run " + run.getFullDisplayName() + ", pipelineListenerStep:" + pipelineStepListener.toString());
            try {
                pipelineStepListener.onStartPipeline(node, run);
            } catch(RuntimeException e) {
                LOGGER.log(Level.WARNING, ()-> "Exception invoking `onStartPipeline` on " + pipelineStepListener);
            }
        }
    }

    public void fireOnAfterEndStageStep(@Nonnull StepEndNode node, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        for(PipelineStepListener pipelineStepListener: PipelineStepListener.all()) {
            log(() -> "onAfterEndStageStep(" + node.getDisplayName() + "): " + pipelineStepListener.toString()  +  (node.getError() != null ? ("error: " + node.getError().getError().toString()) : ""));
            try {
                pipelineStepListener.onAfterEndStageStep(node,stageName, run);
            } catch(RuntimeException e) {
                LOGGER.log(Level.WARNING, ()-> "Exception invoking `onAfterEndStageStep` on " + pipelineStepListener);
            }
        }
    }

    public void fireOnBeforeAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {
        for(PipelineStepListener pipelineStepListener: PipelineStepListener.all()) {
            log(() -> "onBeforeAtomicStep(" + node.getDisplayName() + "): " + pipelineStepListener.toString());
            try {
                pipelineStepListener.onBeforeAtomicStep(node, run);
            } catch(RuntimeException e) {
                LOGGER.log(Level.WARNING, ()-> "Exception invoking `onBeforeAtomicStep` on " + pipelineStepListener);
            }
        }
    }

    public void fireOnBeforeStartStageStep(@Nonnull StepStartNode node, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        for(PipelineStepListener pipelineStepListener: PipelineStepListener.all()) {
            log(() -> "onBeforeStartStageStep(" + node.getDisplayName() + "): " + pipelineStepListener.toString());
            try {
                pipelineStepListener.onBeforeStartStageStep(node, stageName, run);
            } catch(RuntimeException e) {
                LOGGER.log(Level.WARNING, ()-> "Exception invoking `onBeforeStartStageStep` on " + pipelineStepListener);
            }
        }
    }

    private boolean isBeforeEndStageStep(@Nonnull FlowNode node) {
        return (node instanceof StepEndNode) && PipelineNodeUtil.isStage(((StepEndNode) node).getStartNode());
    }

    protected void log(@Nonnull Supplier<String> message) {
        log(Level.FINE, message);
    }
    protected void log(@Nonnull Level level, @Nonnull Supplier<String> message) {
        LOGGER.log(level, message);
    }
}
