/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import static com.google.common.base.Verify.verifyNotNull;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.tasks.BuildStep;
import io.jenkins.plugins.opentelemetry.OpenTelemetryAttributesAction;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.job.action.BuildStepMonitoringAction;
import io.jenkins.plugins.opentelemetry.job.action.FlowNodeMonitoringAction;
import io.jenkins.plugins.opentelemetry.job.action.OtelMonitoringAction;
import io.jenkins.plugins.opentelemetry.job.action.RunPhaseMonitoringAction;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.GraphLookupView;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;

@Extension
public class OtelTraceService {
    private static final Logger LOGGER = Logger.getLogger(OtelTraceService.class.getName());

    /**
     * When {@code true}, the trace service throws exceptions on invalid state rather than logging warnings.
     * Enables strict validation during tests.
     */
    @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
    public static boolean STRICT_MODE = false;

    /** Creates a new OtelTraceService. Instances are managed by Jenkins as an extension. */
    public OtelTraceService() {}

    /**
     * Returns the span of the current run phase.
     *
     * @return the span of the current pipeline run phase:
     * {@link ExtendedJenkinsAttributes#JENKINS_JOB_SPAN_PHASE_START_NAME},
     * {@link ExtendedJenkinsAttributes#JENKINS_JOB_SPAN_PHASE_RUN_NAME},
     * {@link ExtendedJenkinsAttributes#JENKINS_JOB_SPAN_PHASE_FINALIZE_NAME},
     */
    public Span getSpan(@NonNull Run<?, ?> run) {
        return ImmutableList.copyOf(run.getActions(RunPhaseMonitoringAction.class)).reverse().stream()
                .filter(Predicate.not(RunPhaseMonitoringAction::hasEnded))
                .findFirst()
                .map(RunPhaseMonitoringAction::getSpan)
                .orElse(Span.getInvalid());
    }

    /**
     * Returns top level span of the {@link Run}
     */
    @NonNull
    public Span getPipelineRootSpan(@NonNull Run<?, ?> run) {
        return run.getActions(MonitoringAction.class).stream()
                .findFirst()
                .map(MonitoringAction::getSpan)
                .orElse(Span.getInvalid());
    }

    /**
     * Returns the active (non-ended) span associated with the given flow node,
     * walking up the ancestor chain until a span is found. Falls back to the current run phase span.
     *
     * @param run      the workflow run
     * @param flowNode the flow node for which to find the enclosing span
     * @return the span for the given flow node, or the run phase span as a fallback
     */
    @NonNull
    public Span getSpan(@NonNull Run<?, ?> run, FlowNode flowNode) {
        Iterable<FlowNode> ancestors = getAncestors(flowNode);
        for (FlowNode currentFlowNode : ancestors) {
            Optional<Span> span = ImmutableList.copyOf(currentFlowNode.getActions(FlowNodeMonitoringAction.class))
                    .reverse() // from last to first
                    .stream()
                    .filter(Predicate.not(FlowNodeMonitoringAction::hasEnded)) // only the non ended spans
                    .findFirst()
                    .map(FlowNodeMonitoringAction::getSpan);
            if (span.isPresent()) {
                return span.get();
            }
        }

        return getSpan(run);
    }

    /**
     * Returns the active (non-ended) span for the given build step within a freestyle build,
     * falling back to the current run phase span when no build step span is found.
     *
     * @param build     the abstract build
     * @param buildStep the build step for which to find the span
     * @return the span for the build step, or the run phase span as a fallback
     */
    @NonNull
    public Span getSpan(@NonNull AbstractBuild<?, ?> build, @NonNull BuildStep buildStep) {
        return ImmutableList.copyOf(build.getActions(BuildStepMonitoringAction.class))
                .reverse() // from last to first
                .stream()
                .filter(Predicate.not(BuildStepMonitoringAction::hasEnded)) // only the non ended spans
                .findFirst()
                .map(BuildStepMonitoringAction::getSpan)
                .orElseGet(() -> getSpan(build)); // or else get the phase span
    }

    /**
     * Return the chain of enclosing flowNodes including the given flow node. If the given flow node is a step end node,
     * the associated step start node is also added.
     * <p>
     * Example
     * <pre>
     * test-pipeline-with-parallel-step8
     *    |- Phase: Start
     *    |- Phase: Run
     *    |   |- Agent, function: node, name: agent, node.id: 3
     *    |       |- Agent Allocation, function: node, name: agent.allocate, node.id: 3
     *    |       |- Stage: ze-parallel-stage, function: stage, name: ze-parallel-stage, node.id: 6
     *    |           |- Parallel branch: parallelBranch1, function: parallel, name: parallelBranch1, node.id: 10
     *    |           |   |- shell-1, function: sh, name: Shell Script, node.id: 14
     *    |           |- Parallel branch: parallelBranch2, function: parallel, name: parallelBranch2, node.id: 11
     *    |           |   |- shell-2, function: sh, name: Shell Script, node.id: 16
     *    |           |- Parallel branch: parallelBranch3, function: parallel, name: parallelBranch3, node.id: 12
     *    |               |- shell-3, function: sh, name: Shell Script, node.id: 18
     *    |- Phase: Finalise
     * </pre>
     * <p>
     * {@code getAncestors("shell-3/node.id: 18")} will return {@code [
     * "shell-3/node.id: 18",
     * "Parallel branch: parallelBranch3/node.id: 12",
     * "Stage: ze-parallel-stage, node.id: 6",
     * "node / node.id: 3",
     * "Start of Pipeline / node.id: 2" // not visualized above
     * ]}
     * TODO optimize lazing loading the enclosing blocks using {@link GraphLookupView#findEnclosingBlockStart(FlowNode)}
     *
     * @return list of enclosing flow nodes starting with the passed flow nodes
     */
    @NonNull
    private Iterable<FlowNode> getAncestors(@NonNull final FlowNode flowNode) {
        List<FlowNode> ancestors = new ArrayList<>();
        FlowNode startNode;
        if (flowNode instanceof StepEndNode) {
            startNode = ((StepEndNode) flowNode).getStartNode();
        } else {
            startNode = flowNode;
        }
        ancestors.add(startNode);
        ancestors.addAll(startNode.getEnclosingBlocks());
        LOGGER.log(
                Level.FINEST,
                () -> "getAncestors(" + OtelUtils.toDebugString(flowNode) + "): "
                        + ancestors.stream()
                                .map(OtelUtils.flowNodeToDebugString())
                                .collect(Collectors.joining(", ")));
        return ancestors;
    }

    /**
     * Removes the span associated with the given pipeline flow node and closes all
     * OTel {@link io.opentelemetry.context.Scope} objects that were opened for that span.
     *
     * @param run      the workflow run
     * @param flowNode the flow node whose span should be removed
     * @param span     the span to remove
     */
    public void removePipelineStepSpanAndCloseAssociatedScopes(
            @NonNull WorkflowRun run, @NonNull FlowNode flowNode, @NonNull Span span) {
        FlowNode startSpanNode;
        if (flowNode instanceof AtomNode) {
            startSpanNode = flowNode;
        } else if (flowNode instanceof StepEndNode) {
            StepEndNode stepEndNode = (StepEndNode) flowNode;
            startSpanNode = stepEndNode.getStartNode();
        } else if (flowNode instanceof StepStartNode
                && ((StepStartNode) flowNode).getDescriptor() instanceof ExecutorStep.DescriptorImpl) {
            // remove the "node.allocate" span, it's located on the parent node which is also a StepStartNode of a
            // ExecutorStep.DescriptorImpl
            startSpanNode = flowNode.getParents().stream().findFirst().orElse(null);
            if (startSpanNode == null) {
                if (STRICT_MODE) {
                    throw new IllegalStateException(
                            "Parent node NOT found for " + OtelUtils.toDebugString(flowNode) + " on " + run);
                } else {
                    LOGGER.log(
                            Level.WARNING,
                            () -> "Parent node NOT found for " + OtelUtils.toDebugString(flowNode) + " on " + run);
                    return;
                }
            }
        } else {
            throw new VerifyException("Can't remove span from node of type" + flowNode.getClass() + " - " + flowNode);
        }

        ImmutableList.copyOf(startSpanNode.getActions(FlowNodeMonitoringAction.class)).reverse().stream()
                .filter(flowNodeMonitoringAction -> Objects.equals(
                        flowNodeMonitoringAction.getSpanId(),
                        span.getSpanContext().getSpanId()))
                .findFirst()
                .ifPresentOrElse(FlowNodeMonitoringAction::purgeSpanAndCloseAssociatedScopes, () -> {
                    if (!Objects.equals(
                            span, Span.getInvalid())) { // recovery of a previous error, skip the invalid span
                        String msg = "span not found to be purged: " + OtelUtils.toDebugString(span) + " ending "
                                + OtelUtils.toDebugString(startSpanNode) + " in " + run;
                        if (STRICT_MODE) {
                            throw new IllegalStateException(msg);
                        } else {
                            LOGGER.log(Level.WARNING, msg);
                        }
                    }
                });
    }

    /**
     * Removes a pipeline job phase span from the run's tracking state.
     * This is a no-op stub; phase spans are closed by the phase transition logic.
     *
     * @param run  the run whose phase span is being removed
     * @param span the phase span to remove
     */
    public void removeJobPhaseSpan(@NonNull Run<?, ?> run, @NonNull Span span) {}

    /**
     * Removes the span for the given build step and closes all associated OTel scopes.
     *
     * @param build     the abstract build
     * @param buildStep the build step whose span is being removed
     * @param span      the span to remove
     */
    public void removeBuildStepSpan(
            @NonNull AbstractBuild<?, ?> build, @NonNull BuildStep buildStep, @NonNull Span span) {
        ImmutableList.copyOf(build.getActions(BuildStepMonitoringAction.class)).reverse().stream()
                .filter(buildStepMonitoringAction -> Objects.equals(
                        buildStepMonitoringAction.getSpanId(),
                        span.getSpanContext().getSpanId()))
                .findFirst()
                .ifPresentOrElse(BuildStepMonitoringAction::purgeSpanAndCloseAssociatedScopes, () -> {
                    if (!Objects.equals(
                            span, Span.getInvalid())) { // recovery of a previous error, skip the invalid span
                        throw new IllegalStateException("span not found to be purged: " + span + " for " + buildStep);
                    }
                });
    }

    /**
     * Purges all span tracking state for the given run, closing any open scopes.
     * Should be called once a run has been finalized to release resources.
     *
     * @param run the run whose trace state should be purged
     */
    public void purgeRun(@NonNull Run<?, ?> run) {
        run.getActions(OtelMonitoringAction.class).forEach(OtelMonitoringAction::purgeSpanAndCloseAssociatedScopes);
        // TODO verify we don't need this cleanup
        if (run instanceof WorkflowRun workflowRun) {
            List<FlowNode> flowNodesHeads = Optional.ofNullable(workflowRun.getExecution())
                    .map(FlowExecution::getCurrentHeads)
                    .orElse(Collections.emptyList());
            ForkScanner scanner = new ForkScanner();
            scanner.setup(flowNodesHeads);
            StreamSupport.stream(scanner.spliterator(), false)
                    .forEach(flowNode -> flowNode.getActions(OtelMonitoringAction.class)
                            .forEach(OtelMonitoringAction::purgeSpanAndCloseAssociatedScopes));
        }
    }

    /**
     * Associates a root span with the given freestyle build.
     *
     * @param build the freestyle build
     * @param span  the root span for the build
     */
    public void putSpan(@NonNull AbstractBuild<?, ?> build, @NonNull Span span) {
        build.addAction(new MonitoringAction(span));
        LOGGER.log(
                Level.FINEST,
                () -> "putSpan(" + build.getFullDisplayName() + "," + OtelUtils.toDebugString(span) + ")");
    }

    /**
     * Associates a span with a build step within a freestyle build.
     *
     * @param build     the freestyle build
     * @param buildStep the build step
     * @param span      the span for the build step
     */
    public void putSpan(AbstractBuild<?, ?> build, BuildStep buildStep, Span span) {
        build.addAction(new BuildStepMonitoringAction(span));
        LOGGER.log(
                Level.FINEST,
                () -> "putSpan(" + build.getFullDisplayName() + ", " + buildStep + "," + OtelUtils.toDebugString(span)
                        + ")");
    }

    /**
     * Associates a root span with the given run.
     *
     * @param run  the run
     * @param span the root span for the run
     */
    public void putSpan(@NonNull Run<?, ?> run, @NonNull Span span) {
        run.addAction(new MonitoringAction(span));
        LOGGER.log(
                Level.FINEST, () -> "putSpan(" + run.getFullDisplayName() + "," + OtelUtils.toDebugString(span) + ")");
    }

    /**
     * Associates a pipeline run-phase span with the given run and copies any
     * OTel attributes from a previously attached {@link OpenTelemetryAttributesAction}.
     *
     * @param run  the run
     * @param span the phase span
     */
    public void putRunPhaseSpan(@NonNull Run<?, ?> run, @NonNull Span span) {
        run.addAction(new RunPhaseMonitoringAction(span));
        // Phase spans do not get the attributes from the StepContext.
        // To ensure that attributes of child spans of the root span are set correctly we read them from an
        // OpenTelemetryAttributesAction set on the Run.
        setAttributesToSpan(span, run.getAction(OpenTelemetryAttributesAction.class));
        LOGGER.log(
                Level.FINEST,
                () -> "putRunPhaseSpan(" + run.getFullDisplayName() + "," + OtelUtils.toDebugString(span) + ")");
    }

    /**
     * Associates an agent span with the given run and flow node, also propagating
     * any OTel attributes set on the run.
     *
     * @param run      the run
     * @param span     the agent span
     * @param flowNode the flow node representing the agent allocation
     */
    public void putAgentSpan(@NonNull Run<?, ?> run, @NonNull Span span, @NonNull FlowNode flowNode) {
        // Agent spans do not get the attributes from the StepContext.
        // To ensure that attributes of child spans of the root span are set correctly we read them from an
        // OpenTelemetryAttributesAction set on the Run.
        setAttributesToSpan(span, run.getAction(OpenTelemetryAttributesAction.class));
        putSpan(run, span, flowNode);
        LOGGER.log(
                Level.FINEST,
                () -> "putAgentSpan(" + run.getFullDisplayName() + "," + OtelUtils.toDebugString(span) + ")");
    }

    /**
     * Associates a span with the given flow node within a run.
     *
     * @param run      the run
     * @param span     the span to associate
     * @param flowNode the flow node to annotate
     */
    public void putSpan(@NonNull Run<?, ?> run, @NonNull Span span, @NonNull FlowNode flowNode) {
        // FYI for agent allocation, we have 2 FlowNodeMonitoringAction to track the agent allocation duration
        flowNode.addAction(new FlowNodeMonitoringAction(span));

        LOGGER.log(
                Level.FINE,
                () -> "putSpan(" + run.getFullDisplayName() + ", " + OtelUtils.toDebugString(flowNode) + ", "
                        + OtelUtils.toDebugString(span) + ")");
    }

    /**
     * Associates a span and its open OTel scopes with the given flow node within a run.
     * The scopes are closed when {@link #removePipelineStepSpanAndCloseAssociatedScopes} is called.
     *
     * @param run      the run
     * @param span     the span to associate
     * @param flowNode the flow node to annotate
     * @param scopes   the open scopes that must be closed when the span ends
     */
    public void putSpanAndScopes(
            @NonNull Run<?, ?> run, @NonNull Span span, @NonNull FlowNode flowNode, List<Scope> scopes) {
        // FYI for agent allocation, we have 2 FlowNodeMonitoringAction to track the agent allocation duration
        flowNode.addAction(new FlowNodeMonitoringAction(span, scopes));

        LOGGER.log(
                Level.FINE,
                () -> "putSpan(" + run.getFullDisplayName() + ", " + OtelUtils.toDebugString(flowNode) + ", "
                        + OtelUtils.toDebugString(span) + ")");
    }

    private void setAttributesToSpan(@NonNull Span span, OpenTelemetryAttributesAction openTelemetryAttributesAction) {
        if (openTelemetryAttributesAction == null) {
            return;
        }
        for (Map.Entry<AttributeKey<?>, Object> entry :
                openTelemetryAttributesAction.getAttributes().entrySet()) {
            AttributeKey<?> attributeKey = entry.getKey();
            Object value = verifyNotNull(entry.getValue());
            span.setAttribute((AttributeKey<? super Object>) attributeKey, value);
        }
    }

    /**
     * Returns the singleton trace service extension.
     *
     * @return the registered trace service
     */
    public static OtelTraceService get() {
        return ExtensionList.lookupSingleton(OtelTraceService.class);
    }
}
