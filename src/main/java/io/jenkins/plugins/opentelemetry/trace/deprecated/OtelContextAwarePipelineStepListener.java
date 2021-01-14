package io.jenkins.plugins.opentelemetry.trace.deprecated;

import com.google.common.base.Strings;
import io.jenkins.plugins.opentelemetry.pipeline.listener.PipelineAdvancedStepListener;
import io.jenkins.plugins.opentelemetry.pipeline.PipelineNodeUtil;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Verify.verifyNotNull;

/**
 * Adapter to simplify the implementation of pipeline {@link org.jenkinsci.plugins.workflow.steps.Step} listeners.
 */
public abstract class OtelContextAwarePipelineStepListener extends OtelContextAwareAbstractGraphListener implements PipelineAdvancedStepListener {
    private final static Logger LOGGER = Logger.getLogger(OtelContextAwarePipelineStepListener.class.getName());

    @Override
    public final void _onNewHead(FlowNode node) {
        WorkflowRun run = getWorkflowRun(node);
        // log( () -> "_onNewHead" + node.getDisplayFunctionName() + " / " + PipelineNodeUtil.getDisplayName(node) + ", " + ", node.parent: " + Iterables.getFirst(node.getParents(), null));

        FlowNode previousNode = PipelineNodeUtil.getPreviousNode(node);
        if (isBeforePreStartStageStep(previousNode)) {
            String stageName = getPreStageName((StepStartNode) previousNode);
            log(() -> "onAfterPreStartStageStep(" + stageName + ") error: " + (previousNode.getError() != null ? previousNode.getError().getError().toString() : ""));
            onAfterPreStartStageStep((StepStartNode) previousNode, stageName, run);
        } else if (isBeforeStartStageStep(previousNode)) {
            String stageName = PipelineNodeUtil.getDisplayName(previousNode);
            log(() -> "onAfterPreStartStageStep(" + stageName + ") error: " + (previousNode.getError() != null ? previousNode.getError().getError().toString() : ""));
            onAfterStartStageStep((StepStartNode) previousNode, stageName, run);
        } else if (isBeforeEndPreStageStep(previousNode)) {
            String stageName = getPreStageName(((StepEndNode) previousNode).getStartNode());
            log(() -> "onAfterEndPreStageStep(" + stageName + ") error: " + (previousNode.getError() != null ? previousNode.getError().getError().toString() : ""));
            onAfterEndPreStageStep((StepEndNode) previousNode, stageName, run);
        } else if (isBeforeEndStageStep(previousNode)) {
            String stageName = PipelineNodeUtil.getDisplayName(((StepEndNode) previousNode).getStartNode());
            ErrorAction error = previousNode.getError();
            log(() -> "onAfterEndStageStep(" + stageName + ") error: " + (previousNode.getError() != null ? previousNode.getError().getError().toString() : ""));
            onAfterEndStageStep((StepEndNode) previousNode, stageName, run);
        } else if (previousNode instanceof StepAtomNode) {
            StepAtomNode stepAtomNode = (StepAtomNode) previousNode;
            log(() -> "onAfterAtomicStep(" + stepAtomNode.getDisplayName() + ")");
            onAfterAtomicStep(stepAtomNode, run);
        } else {
            log(() -> "ignore previous node " + previousNode);
        }

        if (isStartPipeline(node)) {
            log(() -> "onStartPipeline(" + run.getFullDisplayName() + "))");
            onStartPipeline(node, run);
        } else if (isEndPipeline(node)) {
            log(() -> "onEndPipeline(" + run.getFullDisplayName() + "))");
            onEndPipeline(node, run);
        } else if (isBeforePreStartStageStep(node)) {
            StepStartNode stepStartNode = (StepStartNode) node;
            String stageName = getPreStageName(stepStartNode);
            log(() -> "onBeforeStartPreStageStep(" + stageName + ")");
            onBeforeStartPreStageStep(stepStartNode, stageName, run);
        } else if (isBeforeStartStageStep(node)) {
            StepStartNode stepStartNode = (StepStartNode) node;
            String stageName = PipelineNodeUtil.getDisplayName(stepStartNode);
            log(() -> "onBeforeStartStageStep(" + stageName + ")");
            onBeforeStartStageStep(stepStartNode, stageName, run);
        } else if (isBeforeEndStageStep(node)) {
            StepEndNode stepEndNode = (StepEndNode) node;
            String stageName = PipelineNodeUtil.getDisplayName(stepEndNode.getStartNode());
            log(() -> "onBeforeEndStageStep(" + stageName + ") error: " + Objects.toString(node.getError(), ""));
            onBeforeEndStageStep(stepEndNode, stageName, run);
        } else if (isBeforeEndPreStageStep(node)) {
            StepEndNode stepEndNode = (StepEndNode) node;
            String stageName = getPreStageName(stepEndNode.getStartNode());
            log(() -> "onBeforeEndPreStageStep(" + stageName + ") error: " + Objects.toString(node.getError(), ""));
            onBeforeEndPreStageStep(stepEndNode, stageName, run);
        } else if (node instanceof StepAtomNode) {
            StepAtomNode stepAtomNode = (StepAtomNode) node;
            log(() -> "onBeforeAtomicStep(" + stepAtomNode.getDisplayName() + ")");
            onBeforeAtomicStep(stepAtomNode, run);
        } else {
            log(() -> "on_" + node.getDisplayName() + " - " + node.getDisplayFunctionName());
        }
    }

    @Override
    public void onAfterAtomicStep(StepAtomNode stepAtomNode, WorkflowRun run) {
    }

    @Override
    public void onAfterStartStageStep(StepStartNode previousNode, String stageName, WorkflowRun run) {
    }

    @Override
    public void onAfterPreStartStageStep(StepStartNode previousNode, String stageName, WorkflowRun run) {
    }

    @Override
    public void onEndPipeline(FlowNode node, WorkflowRun run) {
    }

    @Override
    public void onStartPipeline(FlowNode node, WorkflowRun run) {
    }

    @Override
    public void onBeforeEndPreStageStep(StepEndNode stepEndNode, String stageName, WorkflowRun run) {
    }

    @Override
    public void onAfterEndStageStep(@Nonnull StepEndNode stageStepEndNode, @Nonnull String
            stageName, @Nonnull WorkflowRun run) {
    }

    @Override
    public void onBeforeEndStageStep(@Nonnull StepEndNode stepEndNode, String stageName, @Nonnull WorkflowRun
            run) {
    }

    @Override
    public void onBeforeAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {
    }

    @Override
    public void onBeforeStartStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String
            stageName, @Nonnull WorkflowRun run) {
    }

    @Override
    public void onBeforeStartPreStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String
            stageName, @Nonnull WorkflowRun run) {
    }

    @Override
    public void onAfterEndPreStageStep(@Nonnull StepEndNode stepEndNode, @Nonnull String
            stageName, @Nonnull WorkflowRun run) {
    }

    private boolean isEndPipeline(FlowNode node) {
        return node instanceof FlowEndNode;
    }

    protected boolean isStartPipeline(FlowNode node) {
        return node instanceof FlowStartNode;
    }

    protected boolean isBeforeEndStageStep(@Nonnull FlowNode node) {
        return (node instanceof StepEndNode) && PipelineNodeUtil.isStage(((StepEndNode) node).getStartNode());
    }

    protected boolean isAfterEndStageStep(@Nonnull FlowNode node) {
        FlowNode previousNode = PipelineNodeUtil.getPreviousNode(node);
        if (previousNode == null) {
            return false;
        }
        return isBeforeEndStageStep(previousNode);
    }

    protected boolean isAfterEndPreStageStep(@Nonnull FlowNode node) {
        FlowNode previousNode = PipelineNodeUtil.getPreviousNode(node);
        if (previousNode == null) {
            return false;
        }
        return isBeforeEndPreStageStep(previousNode);
    }

    protected boolean isBeforeEndPreStageStep(@Nonnull FlowNode node) {
        if (node instanceof StepEndNode) {
            StepEndNode stepEndNode = (StepEndNode) node;
            if (stepEndNode.getDescriptor() instanceof StageStep.DescriptorImpl) {
                if (isBeforePreStartStageStep(stepEndNode.getStartNode())) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean isBeforeStartStageStep(@Nonnull FlowNode node) {
        return PipelineNodeUtil.isStage(node);
    }

    protected boolean isBeforePreStartStageStep(@Nonnull FlowNode node) {
        if (node instanceof StepStartNode) {
            StepStartNode stepStartNode = (StepStartNode) node;
            if (stepStartNode.getDescriptor() instanceof StageStep.DescriptorImpl) {
                if (stepStartNode.getPersistentAction(LabelAction.class) != null) {
                    return false;
                } else if (stepStartNode.getPersistentAction(ArgumentsAction.class) != null) {
                    // When a stage starts, 2 flow nodes are chained, one with an ArgumentsAction then one with a LabelAction.
                    // We assume that the first node, the node with the ArgumentAction, is the "pre stage"
                    return true;
                } else {
                    // ignore
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Nonnull
    protected String getPreStageName(@Nonnull StepStartNode stepStartNode) {
        ArgumentsAction labelAction = stepStartNode.getPersistentAction(ArgumentsAction.class);
        verifyNotNull(labelAction, "No ArgumentsAction found on given node %s");
        String stageName = Objects.toString(labelAction.getArgumentValue("name"), null);
        if (Strings.isNullOrEmpty(stageName)) {
            stageName = stepStartNode.getDescriptor().getDisplayName() + "#" + stepStartNode.getId();
        }
        return stageName;
    }

    protected void log(@Nonnull Supplier<String> message) {
        LOGGER.log(Level.INFO, message);
    }
}
