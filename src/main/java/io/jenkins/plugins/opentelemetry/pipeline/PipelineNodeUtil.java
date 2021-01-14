package io.jenkins.plugins.opentelemetry.pipeline;

import com.google.common.collect.Iterables;
import hudson.model.Action;
import hudson.model.Queue;
import hudson.model.Result;
import org.jenkinsci.plugins.pipeline.StageStatus;
import org.jenkinsci.plugins.pipeline.SyntheticStage;
import org.jenkinsci.plugins.workflow.actions.*;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.StreamSupport;


public class PipelineNodeUtil {
    /**
     * copy of {@code io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeUtil}
     */
    public static boolean isStage(FlowNode node) {
        // logic below coming {@code io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeUtil#isStage(FlowNode)} from don't work for us
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
        if (node.getAction(LabelAction.class) == null) {
            return false;
        }
        // TODO what about ThreadNameAction.class ?

        return true;
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
                if (action instanceof TagsAction && ((TagsAction) action).getTagValue(SyntheticStage.TAG_NAME) != null) {
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
                return value != null && (value.equals(StageStatus.getSkippedForConditional()) ||
                        value.equals(StageStatus.getSkippedForFailure()) ||
                        value.equals(StageStatus.getSkippedForUnstable())
                );
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
     * copy of {@code io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeUtil}
     */
    public static boolean isParallelBranch(@Nullable FlowNode node) {
        return node != null && node.getAction(LabelAction.class) != null &&
                node.getAction(ThreadNameAction.class) != null;
    }

    /**
     * copy of {@code io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeUtil}
     */
    @Nonnull
    public static String getDisplayName(@Nonnull FlowNode node) {
        ThreadNameAction threadNameAction = node.getAction(ThreadNameAction.class);
        return threadNameAction != null
                ? threadNameAction.getThreadName()
                : node.getDisplayName();
    }

    /**
     * Copy of {@code org.jenkinsci.plugins.githubautostatus.GithubBuildStatusGraphListener#resultForStage}
     *
     * @param startNode
     * @param endNode
     * @return
     */
    @Nonnull
    public static Result resultForStage(FlowNode startNode, FlowNode endNode) {
        Result errorResult = Result.SUCCESS;
        DepthFirstScanner scanner = new DepthFirstScanner();
        if (scanner.setup(endNode, Collections.singletonList(startNode))) {
            WarningAction warningAction = StreamSupport.stream(scanner.spliterator(), false)
                    .map(node -> node.getPersistentAction(WarningAction.class))
                    .filter(Objects::nonNull)
                    .max(Comparator.comparing(warning -> warning.getResult().ordinal))
                    .orElse(null);
            if (warningAction != null) {
                errorResult = warningAction.getResult();
            }
        }
        return errorResult;
    }

    /**
     * Returns the node that has been previously executed
     *
     * @return the {@link FlowNode} that has previously executed or {@code null}
     */
    @CheckForNull
    public static FlowNode getPreviousNode(@Nonnull FlowNode node) {
        return Iterables.getFirst(node.getParents(), null);
    }


    @CheckForNull
    public static WorkflowRun getWorkflowRun(@Nonnull FlowNode flowNode) {
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
}
