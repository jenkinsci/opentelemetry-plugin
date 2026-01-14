/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.jenkins;

import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Action;
import hudson.model.Queue;
import io.jenkins.plugins.opentelemetry.job.step.WithNewSpanStep;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.pipeline.StageStatus;
import org.jenkinsci.plugins.pipeline.SyntheticStage;
import org.jenkinsci.plugins.workflow.actions.*;
import org.jenkinsci.plugins.workflow.cps.actions.ArgumentsActionImpl;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;

public class PipelineNodeUtil {
    private static final Logger LOGGER = Logger.getLogger(PipelineNodeUtil.class.getName());

    /**
     * copy of {@code io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeUtil}
     */
    public static boolean isStartStage(FlowNode node) {
        // logic below coming {@code io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeUtil#isStage(FlowNode)} from
        // don't work for us
        // return node != null && (node.getAction(StageAction.class) != null && !isSyntheticStage(node))
        //        || (node.getAction(LabelAction.class) != null && node.getAction(ThreadNameAction.class) == null));

        if (node == null) {
            return false;
        }

        if (!(node instanceof StepStartNode)) {
            return false;
        }
        StepStartNode stepStartNode = (StepStartNode) node;
        if (!(stepStartNode.getDescriptor() instanceof StageStep.DescriptorImpl)) {
            return false;
        }
        return node.getAction(LabelAction.class) != null;
    }

    public static boolean isStartWithNewSpan(FlowNode node) {
        if (node == null) {
            return false;
        }

        if (!(node instanceof StepStartNode)) {
            return false;
        }
        StepStartNode stepStartNode = (StepStartNode) node;
        if (!(stepStartNode.getDescriptor() instanceof WithNewSpanStep.DescriptorImpl)) {
            return false;
        }
        return node.getAction(ArgumentsActionImpl.class) != null;
    }

    /**
     * copy of {@code io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeUtil}
     */
    public static boolean isSyntheticStage(@Nullable FlowNode node) {
        return node != null && getSyntheticStage(node) != null;
    }

    /**
     * copy of {@code io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeUtil}
     */
    @CheckForNull
    public static TagsAction getSyntheticStage(@Nullable FlowNode node) {
        if (node != null) {
            for (Action action : node.getActions()) {
                if (action instanceof TagsAction
                        && ((TagsAction) action).getTagValue(SyntheticStage.TAG_NAME) != null) {
                    return (TagsAction) action;
                }
            }
        }
        return null;
    }

    /**
     * copy of {@code io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeUtil}
     */
    public static boolean isPostSyntheticStage(@Nullable FlowNode node) {
        if (node == null) {
            return false;
        }
        TagsAction tagsAction = getSyntheticStage(node);
        if (tagsAction == null) {
            return false;
        }
        String value = tagsAction.getTagValue(SyntheticStage.TAG_NAME);
        return value != null && value.equals(SyntheticStage.getPost());
    }

    /**
     * copy of {@code io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeUtil}
     */
    public static boolean isSkippedStage(@Nullable FlowNode node) {
        if (node == null) {
            return false;
        }

        for (Action action : node.getActions()) {
            if (action instanceof TagsAction && ((TagsAction) action).getTagValue(StageStatus.TAG_NAME) != null) {
                TagsAction tagsAction = (TagsAction) action;
                String value = tagsAction.getTagValue(StageStatus.TAG_NAME);
                return value != null
                        && (value.equals(StageStatus.getSkippedForConditional())
                                || value.equals(StageStatus.getSkippedForFailure())
                                || value.equals(StageStatus.getSkippedForUnstable()));
            }
        }
        return false;
    }

    /**
     * copy of {@code io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeUtil}
     */
    public static boolean isPreSyntheticStage(@Nullable FlowNode node) {
        if (node == null) {
            return false;
        }
        TagsAction tagsAction = getSyntheticStage(node);
        if (tagsAction == null) {
            return false;
        }
        String value = tagsAction.getTagValue(SyntheticStage.TAG_NAME);
        return value != null && value.equals(SyntheticStage.getPre());
    }

    /**
     * inspired by of {@code io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeUtil}
     */
    public static boolean isStartParallelBranch(@Nullable FlowNode node) {
        if (node == null) {
            return false;
        }
        if (!(node instanceof StepStartNode)) {
            return false;
        }
        StepStartNode stepStartNode = (StepStartNode) node;
        if (!(stepStartNode.getDescriptor() instanceof ParallelStep.DescriptorImpl)) {
            return false;
        }

        ThreadNameAction threadNameAction = node.getPersistentAction(ThreadNameAction.class);
        return threadNameAction != null;
    }

    public static boolean isStartExecutorNode(@Nullable FlowNode node) {
        if (node == null) {
            return false;
        }
        if (!(node instanceof StepStartNode)) {
            return false;
        }
        StepStartNode stepStartNode = (StepStartNode) node;
        if (!(stepStartNode.getDescriptor() instanceof ExecutorStep.DescriptorImpl)) {
            return false;
        }

        BodyInvocationAction bodyInvocationAction = node.getAction(BodyInvocationAction.class);
        if (bodyInvocationAction != null) {
            // it's the second StepStartNode of the ExecutorStep, the StepStartNode for the actual invocation
            LOGGER.log(Level.FINER, () -> "isStartNode(): false - " + getDetailedDebugString(node));
            return false;
        }

        LOGGER.log(Level.FINE, () -> "isStartNode(): true - " + getDetailedDebugString(node));
        return true;
    }

    /**
     * inspired by of {@code io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeUtil}
     */
    public static boolean isStartParallelBlock(@Nullable FlowNode node) {
        if (node == null) {
            return false;
        }
        if (!(node instanceof StepStartNode)) {
            return false;
        }
        StepStartNode stepStartNode = (StepStartNode) node;
        if (!(stepStartNode.getDescriptor() instanceof ParallelStep.DescriptorImpl)) {
            return false;
        }

        ThreadNameAction threadNameAction = node.getPersistentAction(ThreadNameAction.class);
        return threadNameAction == null;
    }

    public static boolean isEndParallelBlock(@Nullable FlowNode node) {
        if (node == null) {
            return false;
        }
        if (!(node instanceof StepEndNode)) {
            return false;
        }
        StepEndNode stepEndNode = (StepEndNode) node;
        return isStartParallelBlock(stepEndNode.getStartNode());
    }

    public static boolean isStartExecutorNodeExecution(@NonNull FlowNode node) {
        if (node == null) {
            return false;
        }
        if (!(node instanceof StepStartNode)) {
            return false;
        }
        StepStartNode stepStartNode = (StepStartNode) node;
        if (!(stepStartNode.getDescriptor() instanceof ExecutorStep.DescriptorImpl)) {
            return false;
        }

        BodyInvocationAction bodyInvocationAction = node.getAction(BodyInvocationAction.class);
        return bodyInvocationAction != null;
    }

    /**
     * copy of {@code io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeUtil}
     */
    @NonNull
    public static String getDisplayName(@NonNull FlowNode node) {
        ThreadNameAction threadNameAction = node.getAction(ThreadNameAction.class);
        return threadNameAction != null ? threadNameAction.getThreadName() : node.getDisplayName();
    }

    /**
     * Returns the node that has been previously executed
     *
     * @return the {@link FlowNode} that has previously executed or {@code null}
     */
    @CheckForNull
    public static FlowNode getPreviousNode(@NonNull FlowNode node) {
        List<FlowNode> parents = node.getParents();
        if (parents.size() > 1) {
            System.out.println(PipelineNodeUtil.getDetailedDebugString(node));
        }
        return Iterables.getFirst(parents, null);
    }

    @CheckForNull
    public static WorkflowRun getWorkflowRun(@NonNull FlowNode flowNode) {
        Queue.Executable executable;
        try {
            executable = flowNode.getExecution().getOwner().getExecutable();
        } catch (IOException e) {
            // Ignore exception. Likely to be a `new IOException("not implemented")` thrown by
            // org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner.DummyOwner.getExecutable
            return null;
        }

        if (executable instanceof WorkflowRun) {
            return (WorkflowRun) executable;
        }
        return null;
    }

    @NonNull
    public static String getDebugString(@Nullable FlowNode flowNode) {
        if (flowNode == null) {
            return "#null#";
        }
        String value = "Node[" + flowNode.getDisplayFunctionName() + ", "
                + flowNode.getClass().getSimpleName();
        if (flowNode instanceof StepNode) {
            StepNode node = (StepNode) flowNode;
            StepDescriptor descriptor = node.getDescriptor();
            value += "descriptor: "
                    + (descriptor == null ? "#null#" : descriptor.getClass().getName());
        }
        value += "actions: ["
                + flowNode.getActions().stream()
                        .map(action -> action.getClass().getSimpleName())
                        .collect(Collectors.joining(","))
                + "]";
        value += ", id: " + flowNode.getId() + "]";
        return value;
    }

    @NonNull
    public static String getDetailedDebugString(@Nullable FlowNode flowNode) {
        if (flowNode == null) {
            return "#null#";
        }
        String value = "Node[" + flowNode.getDisplayFunctionName() + ", id: " + flowNode.getId() + ", class: "
                + flowNode.getClass().getSimpleName() + ",";
        if (flowNode instanceof StepNode) {
            StepNode node = (StepNode) flowNode;
            StepDescriptor descriptor = node.getDescriptor();
            String descriptorClass = descriptor == null
                    ? "#null#"
                    : StringUtils.substringAfterLast(descriptor.getClass().getName(), ".");
            value += "descriptor: " + descriptorClass + ",";
        }
        if (flowNode instanceof StepEndNode) {
            StepEndNode o = (StepEndNode) flowNode;
            value += "startNode: [id:" + o.getStartNode().getId() + "],";
        }
        value += ", actions: ["
                + flowNode.getActions().stream()
                        .map(action -> action.getClass().getSimpleName())
                        .collect(Collectors.joining(","))
                + "]";
        value += "]";
        return value;
    }
}
