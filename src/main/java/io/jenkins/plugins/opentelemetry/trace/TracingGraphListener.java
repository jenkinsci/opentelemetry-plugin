package io.jenkins.plugins.opentelemetry.trace;

import static com.google.common.base.Verify.*;

import com.google.common.base.Strings;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.trace.context.OtelContextAwareAbstractGraphListener;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class TracingGraphListener extends OtelContextAwareAbstractGraphListener implements GraphListener.Synchronous {
    private final static Logger LOGGER = Logger.getLogger(TracingGraphListener.class.getName());

    @Override
    public void _onNewHead(FlowNode node) {
        if (isPreStartStageStep(node)) {
            String stageName = getPreStageName((StepStartNode) node);
            LOGGER.log(Level.INFO, "{2} - preStartStage {0}, name:{1}, node: {3}", new Object[]{stageName, node.getDisplayName(), node.getDisplayFunctionName(), node});
            WorkflowRun run = getWorkflowRun(node);
            Span stageSpan = getTracer().spanBuilder(stageName)
                    .setParent(Context.current())
                    .setAttribute("jenkins.pipeline.step.type", "stage")
                    .startSpan();
            getOpenTelemetryTracerService().putSpan(run, stageSpan);
        } else if (isStartStageStep(node)) {
            String stageName = getStageName((StepStartNode) node);
            LOGGER.log(Level.INFO, "{2} - startStage {0}, name:{1}, node: {3}", new Object[]{stageName, node.getDisplayName(), node.getDisplayFunctionName(), node});
        } else if (isEndStageStep(node)) {
            StepEndNode stepEndNode = (StepEndNode) node;

            LOGGER.log(Level.INFO, "{2} - endStage {0}, name:{1}, node: {3}", new Object[]{getStageName(stepEndNode.getStartNode()), node.getDisplayName(), node.getDisplayFunctionName(), node});



        } else if (isEndPreStageStep(node)) {
            StepEndNode stepEndNode = (StepEndNode) node;
            WorkflowRun run = getWorkflowRun(node);
            LOGGER.log(Level.INFO, "{2} - endPreStage {0}, name:{1}, node: {3}", new Object[]{getPreStageName(stepEndNode.getStartNode()), node.getDisplayName(), node.getDisplayFunctionName(), node});
            Span stageSpan = getOpenTelemetryTracerService().getSpan(run);
            stageSpan.end();
            getOpenTelemetryTracerService().removeSpan(run, stageSpan);

        } else if (node instanceof StepAtomNode) {
            StepAtomNode stepAtomNode = (StepAtomNode) node;
            StepDescriptor stepAtomNodeDescriptor = stepAtomNode.getDescriptor();
            if (stepAtomNodeDescriptor instanceof SCMStep.SCMStepDescriptor) {
                SCMStep.SCMStepDescriptor scmStepDescriptor = (SCMStep.SCMStepDescriptor) stepAtomNodeDescriptor;
                LOGGER.log(Level.INFO, "{2} - SCM Step - name:{1}, displayFunctionName:{2} - {0}", new Object[]{stepAtomNode, stepAtomNode.getDisplayName(), stepAtomNode.getDisplayFunctionName(), scmStepDescriptor});
            } else {
                LOGGER.log(Level.INFO, "{2} - onNewHead - name:{1}, displayFunctionName:{2} - {0}", new Object[]{node, node.getDisplayName(), node.getDisplayFunctionName()});
            }
        } else {
            LOGGER.log(Level.INFO, "{2} - onNewHead - name:{1}, displayFunctionName:{2} - {0}", new Object[]{node, node.getDisplayName(), node.getDisplayFunctionName()});
        }
    }

    protected boolean isEndStageStep(FlowNode node) {
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
    protected boolean isEndPreStageStep(FlowNode node) {
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
    protected boolean isStartStageStep(FlowNode node) {
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

    protected boolean isPreStartStageStep(FlowNode node) {
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
    private String getStageName(@Nonnull StepStartNode stepStartNode) {
        LabelAction labelAction = stepStartNode.getPersistentAction(LabelAction.class);
        verifyNotNull(labelAction, "No LAbelAction found on given node %s");
        String stageName = labelAction.getDisplayName();
        if (Strings.isNullOrEmpty(stageName)) {
            stageName = stepStartNode.getDescriptor().getDisplayName() + "#" + stepStartNode.getId();
        }
        return stageName;
    }
    @Nonnull
    private String getPreStageName(@Nonnull StepStartNode stepStartNode) {
        ArgumentsAction labelAction = stepStartNode.getPersistentAction(ArgumentsAction.class);
        verifyNotNull(labelAction, "No ArgumentsAction found on given node %s");
        String stageName = Objects.toString(labelAction.getArgumentValue("name"), null);
        if (Strings.isNullOrEmpty(stageName)) {
            stageName = stepStartNode.getDescriptor().getDisplayName() + "#" + stepStartNode.getId();
        }
        return stageName;
    }

}
