package io.jenkins.plugins.opentelemetry.job.jenkins;

import static com.google.common.base.Verify.*;
import com.google.common.collect.Iterables;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Adapter to simplify the implementation of pipeline {@link org.jenkinsci.plugins.workflow.steps.Step} listeners.
 */
@Extension
public class GraphListenerAdapterToPipelineListener implements GraphListener, GraphListener.Synchronous {
    private final static Logger LOGGER = Logger.getLogger(GraphListenerAdapterToPipelineListener.class.getName());

    @Override
    public final void onNewHead(FlowNode node) {
        WorkflowRun run = PipelineNodeUtil.getWorkflowRun(node);
        log(Level.INFO, () -> run.getFullDisplayName() + " - Process " + PipelineNodeUtil.getDetailedDebugString(node));
        for (FlowNode previousNode : node.getParents()) {
            log(Level.INFO, () -> run.getFullDisplayName() + " - Process previous node " + PipelineNodeUtil.getDetailedDebugString(previousNode) + " of node " + PipelineNodeUtil.getDetailedDebugString(node));
            if (previousNode instanceof StepAtomNode) {
                StepAtomNode stepAtomNode = (StepAtomNode) previousNode;
                fireOnAfterAtomicStep(stepAtomNode, run);
            } else if (isBeforeEndStageStep(previousNode)) {
                String stageName = PipelineNodeUtil.getDisplayName(((StepEndNode) previousNode).getStartNode());
                fireOnAfterEndStageStep((StepEndNode) previousNode, stageName, run);
            } else if (isBeforeEndParallelBranch(previousNode)) {
                StepEndNode endParallelBranchNode = (StepEndNode) previousNode;
                StepStartNode beginParallelBranch = endParallelBranchNode.getStartNode();
                ThreadNameAction persistentAction = verifyNotNull(beginParallelBranch.getPersistentAction(ThreadNameAction.class), "Null ThreadNameAction on %s", beginParallelBranch);
                fireOnAfterEndParallelStepBranch(endParallelBranchNode, persistentAction.getThreadName(), run);
            } else {
                log(Level.INFO, () -> "Ignore previous node " + PipelineNodeUtil.getDetailedDebugString(previousNode));
            }
        }

        if (node instanceof FlowStartNode) {
            fireOnStartPipeline((FlowStartNode) node, run);
        } else if (node instanceof FlowEndNode) {
            fireOnEndPipeline((FlowEndNode) node, run);
        } else if (node instanceof StepAtomNode) {
            fireOnBeforeAtomicStep((StepAtomNode) node, run);
        } else if (PipelineNodeUtil.isStartStage(node)) {
            String stageName = PipelineNodeUtil.getDisplayName(node);
            fireOnBeforeStartStageStep((StepStartNode) node, stageName, run);
        } else if (PipelineNodeUtil.isStartParallelBranch(node)) {
            ThreadNameAction persistentAction = verifyNotNull(node.getPersistentAction(ThreadNameAction.class), "Null ThreadNameAction on %s", node);
            fireOnBeforeStartParallelStepBranch((StepStartNode) node, persistentAction.getThreadName(), run);
        } else if (PipelineNodeUtil.isStartParallelBlock(node)) {
            // begin parallel block
        } else if (PipelineNodeUtil.isEndParallelBlock(node)) {
            // ignore
        } else if (PipelineNodeUtil.isStartNode(node)) {
            StepStartNode stepStartNode = (StepStartNode) node;
            // we don't know how to surface the requested labels.
            // ignore
            LOGGER.log(Level.INFO, "begin node{} block " + PipelineNodeUtil.getDetailedDebugString(stepStartNode) );
        } else {
            logFlowNodeDetails(node, run);
        }
    }

    private void fireOnBeforeStartParallelStepBranch(@Nonnull StepStartNode node, @Nonnull String branchName, @Nonnull WorkflowRun run) {
        for (PipelineListener pipelineListener : PipelineListener.all()) {
            log(Level.INFO, () -> "onBeforeStartParallelStepBranch(branchName: " + branchName + ", " + node.getDisplayName() + "): " + pipelineListener.toString());
            try {
                pipelineListener.onStartParallelStepBranch(node, branchName, run);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, e, () -> "Exception invoking `onBeforeStartParallelStepBranch` on " + pipelineListener);
            }
        }
    }

    private void fireOnAfterEndParallelStepBranch(@Nonnull StepEndNode node, @Nonnull String branchName, @Nonnull WorkflowRun run) {
        for (PipelineListener pipelineListener : PipelineListener.all()) {
            log(Level.INFO, () -> "onAfterEndParallelStepBranch(branchName: " + branchName + ", node[name:" + node.getDisplayName() + ", id: " + node.getId() + "]): " + pipelineListener.toString());
            try {
                pipelineListener.onEndParallelStepBranch(node, branchName, run);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, e, () -> "Exception invoking `onAfterEndParallelStepBranch` on " + pipelineListener);
            }
        }
    }

    private void logFlowNodeDetails(@Nonnull FlowNode node, @Nonnull WorkflowRun run) {
        log(Level.INFO, () ->
        {
            String message = run.getFullDisplayName() + " - " +
                    "before " + node.getDisplayFunctionName() + " // " + PipelineNodeUtil.getDisplayName(node) + ", ";

            if (node instanceof StepNode) {
                StepNode stepNode = (StepNode) node;
                StepDescriptor descriptor = stepNode.getDescriptor();
                message += "descriptor (class:" + descriptor.getClass().getName() + ", " + descriptor.getFunctionName() + "), ";
            }
            message += node.getAllActions().stream().map(action -> Objects.toString(action.getDisplayName(), action.getClass().toString())).collect(Collectors.joining(", "));
            message += ", node.parent: " + Iterables.getFirst(node.getParents(), null);
            return message;
        });
    }

    public void fireOnAfterAtomicStep(@Nonnull StepAtomNode stepAtomNode, @Nonnull WorkflowRun run) {
        for (PipelineListener pipelineListener : PipelineListener.all()) {
            log(() -> "onAfterAtomicStep(" + stepAtomNode.getDisplayName() + "): " + pipelineListener.toString());
            try {
                pipelineListener.onAfterAtomicStep(stepAtomNode, run);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, e, () -> "Exception invoking `onAfterAtomicStep` on " + pipelineListener);
            }
        }
    }

    public void fireOnEndPipeline(@Nonnull FlowEndNode node, @Nonnull WorkflowRun run) {
        for (PipelineListener pipelineListener : PipelineListener.all()) {
            log(() -> "onEndPipeline(" + node.getDisplayName() + "): " + pipelineListener.toString());
            try {
                pipelineListener.onEndPipeline(node, run);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, e, () -> "Exception invoking `onEndPipeline` on " + pipelineListener);
            }
        }
    }

    public void fireOnStartPipeline(@Nonnull FlowStartNode node, @Nonnull WorkflowRun run) {
        for (PipelineListener pipelineListener : PipelineListener.all()) {
            log(() -> "onStartPipeline(" + node.getDisplayName() + ") run " + run.getFullDisplayName() + ", pipelineListenerStep:" + pipelineListener.toString());
            try {
                pipelineListener.onStartPipeline(node, run);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, e, () -> "Exception invoking `onStartPipeline` on " + pipelineListener);
            }
        }
    }

    public void fireOnAfterEndStageStep(@Nonnull StepEndNode node, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        for (PipelineListener pipelineListener : PipelineListener.all()) {
            log(() -> "onAfterEndStageStep(" + node.getDisplayName() + "): " + pipelineListener.toString() + (node.getError() != null ? ("error: " + node.getError().getError().toString()) : ""));
            try {
                pipelineListener.onEndStageStep(node, stageName, run);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, e, () -> "Exception invoking `onAfterEndStageStep` on " + pipelineListener);
            }
        }
    }

    public void fireOnBeforeAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {
        for (PipelineListener pipelineListener : PipelineListener.all()) {
            log(() -> "onBeforeAtomicStep(" + node.getDisplayName() + "): " + pipelineListener.toString());
            try {
                pipelineListener.onAtomicStep(node, run);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, e, () -> "Exception invoking `onBeforeAtomicStep` on " + pipelineListener);
            }
        }
    }

    public void fireOnBeforeStartStageStep(@Nonnull StepStartNode node, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        for (PipelineListener pipelineListener : PipelineListener.all()) {
            log(() -> "onBeforeStartStageStep(" + node.getDisplayName() + "): " + pipelineListener.toString());
            try {
                pipelineListener.onStartStageStep(node, stageName, run);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, e, () -> "Exception invoking `onBeforeStartStageStep` on " + pipelineListener);
            }
        }
    }

    private boolean isBeforeEndStageStep(@Nonnull FlowNode node) {
        return (node instanceof StepEndNode) && PipelineNodeUtil.isStartStage(((StepEndNode) node).getStartNode());
    }

    private boolean isBeforeEndParallelBranch(@Nonnull FlowNode node) {
        return (node instanceof StepEndNode) && PipelineNodeUtil.isStartParallelBranch(((StepEndNode) node).getStartNode());
    }

    protected void log(@Nonnull Supplier<String> message) {
        log(Level.FINE, message);
    }

    protected void log(@Nonnull Level level, @Nonnull Supplier<String> message) {
        LOGGER.log(level, message);
    }
}
