package io.jenkins.plugins.opentelemetry.trace;

import com.google.common.base.Strings;
import io.jenkins.plugins.opentelemetry.trace.context.OtelContextAwareAbstractGraphListener;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.cps.CpsBodyInvoker;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Verify.verifyNotNull;

/**
 * Adapter to simplify the implementation of pipeline {@link org.jenkinsci.plugins.workflow.steps.Step} listeners.
 */
public abstract class OtelContextAwarePipelineStepListener extends OtelContextAwareAbstractGraphListener {
    private final static Logger LOGGER = Logger.getLogger(OtelContextAwarePipelineStepListener.class.getName());

    @Override
    public final void _onNewHead(FlowNode node) {
        {
            ErrorAction error = node.getError();
            if (error != null) {
                LOGGER.warning("FOUND ERROR " + error + " on " + node);
            }
            FlowExecution flowExecution = node.getExecution();
            Throwable causeOfFailure = flowExecution.getCauseOfFailure();
            if (causeOfFailure != null) {
                LOGGER.warning("FOUND FAILURE " + causeOfFailure + " on " + node);
            }
        }
        if (isPreStartStageStep(node)) {
            StepStartNode stepStartNode = (StepStartNode) node;
            String stageName = getPreStageName(stepStartNode);
            WorkflowRun run = getWorkflowRun(node);
            LOGGER.log(Level.INFO, "{2} - preStartStage {0}, name:{1}, node: {3}", new Object[]{stageName, node.getDisplayName(), node.getDisplayFunctionName(), node});
            onStartPreStageStep(stepStartNode, stageName, run);
        } else if (isStartStageStep(node)) {
            StepStartNode stepStartNode = (StepStartNode) node;
            WorkflowRun run = getWorkflowRun(node);
            String stageName = getStageName(stepStartNode);
            LOGGER.log(Level.INFO, "{2} - startStage {0}, name:{1}, node: {3}", new Object[]{stageName, node.getDisplayName(), node.getDisplayFunctionName(), node});

            onStartStageStep(stepStartNode, stageName, run);
        } else if (isEndStageStep(node)) {
            StepEndNode stepEndNode = (StepEndNode) node;
            WorkflowRun run = getWorkflowRun(node);
            LOGGER.log(Level.INFO, "{2} - endStage {0}, name:{1}, node: {3}", new Object[]{getStageName(stepEndNode.getStartNode()), node.getDisplayName(), node.getDisplayFunctionName(), node});
            onEndStageStep(stepEndNode, run);
        } else if (isEndPreStageStep(node)) {
            StepEndNode stepEndNode = (StepEndNode) node;
            WorkflowRun run = getWorkflowRun(node);
            LOGGER.log(Level.INFO, "{2} - endPreStage {0}, name:{1}, node: {3}", new Object[]{getPreStageName(stepEndNode.getStartNode()), node.getDisplayName(), node.getDisplayFunctionName(), node});
            onEndPreStageStep(stepEndNode, run);
        } else if (node instanceof StepAtomNode) {
            StepAtomNode stepAtomNode = (StepAtomNode) node;
            WorkflowRun run = getWorkflowRun(node);
            onAtomicStep(stepAtomNode, run);
        } else {
            LOGGER.log(Level.INFO, "{2} - onNewHead - name:{1}, displayFunctionName:{2} - {0}", new Object[]{node, node.getDisplayName(), node.getDisplayFunctionName()});
        }
    }

    protected void onEndStageStep(@Nonnull StepEndNode stepEndNode, @Nonnull WorkflowRun run) {
    }

    protected void onAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {
    }

    protected void onStartStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {
    }

    protected void onEndPreStageStep(@Nonnull StepEndNode stepEndNode, @Nonnull WorkflowRun run) {
    }

    protected void onStartPreStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {
    }

    protected boolean isEndStageStep(@Nonnull FlowNode node) {
        if (node instanceof StepEndNode) {
            StepEndNode stepEndNode = (StepEndNode) node;
            if (stepEndNode.getDescriptor() instanceof StageStep.DescriptorImpl) {
                if (isStartStageStep(stepEndNode.getStartNode())) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    protected boolean isEndPreStageStep(@Nonnull FlowNode node) {
        if (node instanceof StepEndNode) {
            StepEndNode stepEndNode = (StepEndNode) node;
            if (stepEndNode.getDescriptor() instanceof StageStep.DescriptorImpl) {
                if (isPreStartStageStep(stepEndNode.getStartNode())) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    protected boolean isStartStageStep(@Nonnull FlowNode node) {
        if (node instanceof StepStartNode) {
            StepStartNode stepStartNode = (StepStartNode) node;
            if (stepStartNode.getDescriptor() instanceof StageStep.DescriptorImpl) {
                if (stepStartNode.getPersistentAction(LabelAction.class) != null) {
                    return true;
                } else if (stepStartNode.getPersistentAction(ArgumentsAction.class) != null) {
                    // Ignore.
                    // When a stage starts, 2 flow nodes are chained, one with an ArgumentsAction then one with a LabelAction.
                    // We just look at the FlowNode with a LabelAction, we ignore the one with an ArgumentAction
                    return false;
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

    protected boolean isPreStartStageStep(@Nonnull FlowNode node) {
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
    protected String getStageName(@Nonnull StepStartNode stepStartNode) {
        LabelAction labelAction = stepStartNode.getPersistentAction(LabelAction.class);
        verifyNotNull(labelAction, "No LAbelAction found on given node %s");
        String stageName = labelAction.getDisplayName();
        if (Strings.isNullOrEmpty(stageName)) {
            stageName = stepStartNode.getDescriptor().getDisplayName() + "#" + stepStartNode.getId();
        }
        return stageName;
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

    /**
     * @see CpsBodyInvoker#withCallback(org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback)
     */
    public static class MyBodyExecutionCallback extends BodyExecutionCallback {
        @Override
        public void onSuccess(StepContext context, Object result) {

        }

        @Override
        public void onFailure(StepContext context, Throwable t) {

        }
    }
}
