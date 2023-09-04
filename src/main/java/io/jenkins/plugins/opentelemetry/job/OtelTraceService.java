/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.base.VerifyException;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.tasks.BuildStep;
import io.jenkins.plugins.opentelemetry.FlowNodeMonitoringAction;
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import net.jcip.annotations.Immutable;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Verify.verify;

@Extension
public class OtelTraceService implements OtelComponent {
    private static final Logger LOGGER = Logger.getLogger(OtelTraceService.class.getName());

    private transient ConcurrentMap<RunIdentifier, RunSpans> spansByRun;

    private transient ConcurrentMap<RunIdentifier, FreestyleRunSpans> freestyleSpansByRun;

    private Tracer tracer;

    private final Tracer noOpTracer = TracerProvider.noop().get("jenkins");

    public OtelTraceService() {
        initialize();
    }

    protected Object readResolve() {
        initialize();
        return this;
    }

    private void initialize() {
        spansByRun = new ConcurrentHashMap();
        freestyleSpansByRun = new ConcurrentHashMap();
    }

    @NonNull
    public Span getSpan(@NonNull Run run) {
        return getSpan(run, true);
    }

    /**
     * Returns the span of the current run phase.
     *
     * @param run
     * @param verifyIfRemainingSteps
     * @return the span of the current pipeline run phase:
     * {@link io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes#JENKINS_JOB_SPAN_PHASE_START_NAME},
     * {@link io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes#JENKINS_JOB_SPAN_PHASE_RUN_NAME},
     * {@link io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes#JENKINS_JOB_SPAN_PHASE_FINALIZE_NAME},
     * @throws VerifyException if there are ongoing step spans and {@code verifyIfRemainingSteps} is set to {@code true}
     */
    @NonNull
    public Span getSpan(@NonNull Run run, boolean verifyIfRemainingSteps) throws VerifyException {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = spansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new RunSpans()); // absent when Jenkins restarts during build

        if (verifyIfRemainingSteps) {
            verify(runSpans.pipelineStepSpansByFlowNodeId.isEmpty(), run.getFullDisplayName() + " - Can't access run phase span while there are remaining pipeline step spans: " + runSpans);
        }
        LOGGER.log(Level.FINEST, () -> "getSpan(" + run.getFullDisplayName() + ") - " + runSpans);
        final Span span = Iterables.getLast(runSpans.runPhasesSpans, Span.getInvalid());
        if (span == Span.getInvalid()) {
            LOGGER.log(Level.FINE, () -> "No span found for run " + run.getFullDisplayName() + ", Jenkins server may have restarted");
        }
        Span spanV2 = getSpanV2(run, verifyIfRemainingSteps);
        assertEquals(span, spanV2, run);
        return span;
    }

    /**
     * Returns the span of the current {@link Run} phase (start, run, finalize...)
     */
    public Span getSpanV2(@NonNull Run run, boolean verifyIfRemainingSteps) throws VerifyException {
        return Iterables.getLast(run.getActions(MonitoringAction.class)).getSpan();
    }

    /**
     * Returns top level span of the {@link Run}
     */
    @NonNull
    public Span getPipelineRootSpan(@NonNull Run run) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = spansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new RunSpans()); // absent when Jenkins restarts during build
        Span result = runSpans.runPhasesSpans.stream().findFirst().orElse(Span.getInvalid());

        Span resultV2 = getPipelineRootSpanV2(run);
        assertEquals(result, resultV2, run);

        return result; // absent/invalid when Jenkins restarts during build
    }

    protected void assertEquals(@Nullable Span expectedSpan, @Nullable Span actualSpan, @NonNull Run run) {
        Optional<String> actualSpanId = Optional.ofNullable(actualSpan).map(span -> span.getSpanContext().getSpanId());
        Optional<String> expectedSpanId = Optional.ofNullable(expectedSpan).map(span -> span.getSpanContext().getSpanId());
        if (!Objects.equals(actualSpanId, expectedSpanId)) {
            String actualSpanName = actualSpan instanceof ReadWriteSpan ? ((ReadWriteSpan) actualSpan).getName() : null; // when tracer is no-op, span is NOT a ReadWriteSpan
            String expectedSpanName = expectedSpan instanceof ReadWriteSpan ? ((ReadWriteSpan) expectedSpan).getName() : null; // when tracer is no-op, span is NOT a ReadWriteSpan
            LOGGER.log(Level.WARNING,"Span id mismatch: expected=" + expectedSpanId + " / '" + expectedSpanName +
                "', actual=" + actualSpanId + " / '" + actualSpanName +
                "' for run " + run);
        }
    }

    protected void assertEquals(@Nullable Span expectedSpan, @Nullable Span actualSpan, @NonNull FlowNode flowNode, @NonNull Run run) {
        Optional<String> actualSpanId = Optional.ofNullable(actualSpan).map(span -> span.getSpanContext().getSpanId());
        Optional<String> expectedSpanId = Optional.ofNullable(expectedSpan).map(span -> span.getSpanContext().getSpanId());
        if (!Objects.equals(actualSpanId, expectedSpanId)) {
            String msg = "Span id mismatch: expected=" + expectedSpanId + ", actual=" + actualSpanId + " for " +
                "FlowNode[name" + flowNode.getDisplayName() + ", function:" + flowNode.getDisplayFunctionName() + ", id=" + flowNode.getId() + "], run " + run.getFullDisplayName();
            LOGGER.log(Level.WARNING, msg);
            throw new IllegalStateException(msg);
        }
    }

    /**
     * Returns top level span of the {@link Run}
     */
    public Span getPipelineRootSpanV2(@NonNull Run run) {
        List<MonitoringAction> monitoringActions = run.getActions(MonitoringAction.class);
        if (monitoringActions.isEmpty()) {
            LOGGER.log(Level.WARNING, "No MonitoringAction found for " + run);
        } else if (monitoringActions.size() > 1) {
            LOGGER.log(Level.WARNING, "More or less than 1 (" + monitoringActions.size() + ") monitoring action found for " + run.getFullDisplayName() + ": " + monitoringActions);
        }
        return monitoringActions.stream().findFirst().map(MonitoringAction::getSpan).orElse(Span.getInvalid());
    }

    @NonNull
    public Span getSpan(@NonNull Run run, FlowNode flowNode) {

        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = spansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new RunSpans()); // absent when Jenkins restarts during build
        LOGGER.log(Level.FINEST, () -> "getSpan(" + run.getFullDisplayName() + ", FlowNode[name" + flowNode.getDisplayName() + ", function:" + flowNode.getDisplayFunctionName() + ", id=" + flowNode.getId() + "]) -  " + runSpans);
        LOGGER.log(Level.FINEST, () -> "parentFlowNodes: " + flowNode.getParents().stream().map(node -> node.getDisplayName() + ", id: " + node.getId()).collect(Collectors.toList()));

        Iterable<FlowNode> ancestors = getAncestors(flowNode);

        for (FlowNode ancestor : ancestors) {
            final Collection<PipelineSpanContext> pipelineSpanContexts = runSpans.pipelineStepSpansByFlowNodeId.get(ancestor.getId());
            PipelineSpanContext pipelineSpanContext = Iterables.getLast(pipelineSpanContexts, null);
            if (pipelineSpanContext != null) {
                return pipelineSpanContext.getSpan();
            }
        }
        final Span span = Iterables.getLast(runSpans.runPhasesSpans, Span.getInvalid());
        Span resultV2 = getSpanV2(flowNode, run);
        assertEquals(span, resultV2, flowNode, run);

        if (span == Span.getInvalid()) {
            LOGGER.log(Level.FINE, () -> "No span found for run " + run.getFullDisplayName() + ", Jenkins server may have restarted");
        } else {
            LOGGER.log(Level.FINEST, () -> "getSpan(): " + span.getSpanContext().getSpanId());
        }
        return span;
    }

    @NonNull
    public Span getSpanV2(@NonNull FlowNode flowNode, @NonNull Run run) {
        Iterable<FlowNode> ancestors = getAncestors(flowNode);
        for (FlowNode currentFlowNode : ancestors) {
            List<FlowNodeMonitoringAction> flowNodeMonitoringActions = new ArrayList<>(currentFlowNode.getActions(FlowNodeMonitoringAction.class));

            Optional<Span> span = Optional.ofNullable(Iterables.getLast(flowNodeMonitoringActions, null)).map(FlowNodeMonitoringAction::getSpan);
            if (span.isPresent()) {
                return span.get();
            }
        }
        return getSpanV2(run, false);
    }

    @NonNull
    public Span getSpan(@NonNull AbstractBuild build, @NonNull BuildStep buildStep) {

        RunIdentifier runIdentifier = RunIdentifier.fromBuild(build);
        FreestyleRunSpans freestyleRunSpans = freestyleSpansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new FreestyleRunSpans()); // absent when Jenkins restarts during build
        LOGGER.log(Level.FINEST, () -> "getSpan(" + build.getFullDisplayName() + ", BuildStep[name" + buildStep.getClass().getSimpleName() + ") -  " + freestyleRunSpans);

        final Span span = Iterables.getLast(freestyleRunSpans.runPhasesSpans, null);
        if (span == null) {
            LOGGER.log(Level.FINE, () -> "No span found for run " + build.getFullDisplayName() + ", Jenkins server may have restarted");
            return noOpTracer.spanBuilder("noop-recovery-run-span-for-" + build.getFullDisplayName()).startSpan();
        }
        LOGGER.log(Level.FINEST, () -> "span: " + span.getSpanContext().getSpanId());
        return span;
    }

    /**
     * Return the chain of enclosing flowNodes including the given flow node. If the given flow node is a step end node,
     * the associated step start node is also added.
     * <p>
     * Example
     * <pre>
     * test-pipeline-with-parallel-step8
     *    |
     *    |- Phase: Start
     *    |
     *    |- Phase: Run
     *    |   |
     *    |   |- Agent, function: node, name: agent, node.id: 3
     *    |       |
     *    |       |- Agent Allocation, function: node, name: agent.allocate, node.id: 3
     *    |       |
     *    |       |- Stage: ze-parallel-stage, function: stage, name: ze-parallel-stage, node.id: 6
     *    |           |
     *    |           |- Parallel branch: parallelBranch1, function: parallel, name: parallelBranch1, node.id: 10
     *    |           |   |
     *    |           |   |- shell-1, function: sh, name: Shell Script, node.id: 14
     *    |           |
     *    |           |- Parallel branch: parallelBranch2, function: parallel, name: parallelBranch2, node.id: 11
     *    |           |   |
     *    |           |   |- shell-2, function: sh, name: Shell Script, node.id: 16
     *    |           |
     *    |           |- Parallel branch: parallelBranch3, function: parallel, name: parallelBranch3, node.id: 12
     *    |               |
     *    |               |- shell-3, function: sh, name: Shell Script, node.id: 18
     *    |
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
     * TODO optimize lazing loading the enclosing blocks using {@link org.jenkinsci.plugins.workflow.graph.GraphLookupView#findEnclosingBlockStart(org.jenkinsci.plugins.workflow.graph.FlowNode)}
     *
     * @param flowNode
     * @return list of enclosing flow nodes starting with the passed flow nodes
     */
    @NonNull
    private Iterable<FlowNode> getAncestors(@NonNull final FlowNode flowNode) {
        // troubleshoot https://github.com/jenkinsci/opentelemetry-plugin/issues/197
        LOGGER.log(Level.FINEST, () -> "> getAncestorsV2([" + flowNode.getClass().getSimpleName() + ", " + flowNode.getId() + ", '" + flowNode.getDisplayFunctionName() + "'])");
        List<FlowNode> ancestors = new ArrayList<>();
        FlowNode startNode;
        if (flowNode instanceof StepEndNode) {
            startNode = ((StepEndNode) flowNode).getStartNode();
        } else {
            startNode = flowNode;
        }
        ancestors.add(startNode);
        ancestors.addAll(startNode.getEnclosingBlocks());
        // troubleshoot https://github.com/jenkinsci/opentelemetry-plugin/issues/197
        LOGGER.log(Level.FINEST, () -> "< getAncestorsV2([" + flowNode.getClass().getSimpleName() + ", " + flowNode.getId() + ", '" + flowNode.getDisplayFunctionName() + "']): " + ancestors.stream().map(fn -> "[" + fn.getId() + ", " + fn.getDisplayFunctionName() + "]").collect(Collectors.joining(", ")));
        return ancestors;
    }

    public void removePipelineStepSpan(@NonNull Run run, @NonNull FlowNode flowNode, @NonNull Span span) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = this.spansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new RunSpans()); // absent when Jenkins restarts during build

        FlowNode startSpanNode;
        if (flowNode instanceof AtomNode) {
            startSpanNode = flowNode;
        } else if (flowNode instanceof StepEndNode) {
            StepEndNode stepEndNode = (StepEndNode) flowNode;
            startSpanNode = stepEndNode.getStartNode();
        } else if (flowNode instanceof StepStartNode &&
            ((StepStartNode) flowNode).getDescriptor() instanceof ExecutorStep.DescriptorImpl) {
            // remove the "node.allocate" span, it's located on the parent node which is also a StepStartNode of a ExecutorStep.DescriptorImpl
            startSpanNode = Iterables.getFirst(flowNode.getParents(), null);
        } else {
            throw new VerifyException("Can't remove span from node of type" + flowNode.getClass() + " - " + flowNode);
        }
        final Collection<PipelineSpanContext> pipelineSpanContexts = runSpans.pipelineStepSpansByFlowNodeId.get(startSpanNode.getId());
        PipelineSpanContext pipelineSpanContext = Iterables.getLast(pipelineSpanContexts, null);
        if (pipelineSpanContext == null) {
            LOGGER.log(Level.FINE, () -> "Silently ignore removing missing span context for node [id=" + flowNode.getId() + ", function: " + flowNode.getDisplayFunctionName() + "] of run " + run.getFullDisplayName() + ". Jenkins may have restarted");
            return;
        }
        final boolean removed = pipelineSpanContexts.remove(pipelineSpanContext);
        verify(removed, "%s - Failure to remove span %s for node %s: %s", run, pipelineSpanContext, startSpanNode, span, runSpans);

    }

    public void removeJobPhaseSpan(@NonNull Run run, @NonNull Span span) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = this.spansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new RunSpans()); // absent when Jenkins restarts during build

        verify(runSpans.pipelineStepSpansByFlowNodeId.isEmpty(), "%s - Try to remove span associated with a run phase even though there are remain spans associated with flow nodes: %s", run, runSpans);

        Span lastSpan = Iterables.getLast(runSpans.runPhasesSpans, null);
        if (lastSpan == null) {
            LOGGER.log(Level.FINE, () -> "No span found for run " + run.getFullDisplayName() + ", Jenkins server may have restarted");
            return;
        }

        if (Objects.equals(span, lastSpan)) {
            boolean removed = runSpans.runPhasesSpans.remove(span);
            verify(removed, run.getFullDisplayName() + "Failure to remove span from runPhasesSpans: " + span);
            return;
        }

        throw new VerifyException(run.getFullDisplayName() + " - Failure to remove span " + span + " - " + runSpans);
    }

    public void removeBuildStepSpan(@NonNull AbstractBuild build, @NonNull BuildStep buildStep, @NonNull Span span) {
        RunIdentifier runIdentifier = RunIdentifier.fromBuild(build);
        FreestyleRunSpans freestyleRunSpans = this.freestyleSpansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new FreestyleRunSpans()); // absent when Jenkins restarts during build

        Span lastSpan = Iterables.getLast(freestyleRunSpans.runPhasesSpans, null);
        if (lastSpan == null) {
            LOGGER.log(Level.FINE, () -> "No span found for run " + build.getFullDisplayName() + ", Jenkins server may have restarted");
            return;
        }

        if (Objects.equals(span, lastSpan)) {
            boolean removed = freestyleRunSpans.runPhasesSpans.remove(span);
            verify(removed, build.getFullDisplayName() + "Failure to remove span from runPhasesSpans: " + span);
            return;
        }

        throw new VerifyException(build.getFullDisplayName() + " - Failure to remove span " + span + " - " + freestyleRunSpans);
    }

    public void purgeRun(@NonNull Run run) {
        purgeRunV2(run);
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = this.spansByRun.remove(runIdentifier);
        if (runSpans == null) {
            return;
        }

        if (!runSpans.runPhasesSpans.isEmpty() || !runSpans.pipelineStepSpansByFlowNodeId.isEmpty()) {
            throw new VerifyException(run.getFullDisplayName() + " - Some spans have not been ended and removed: " + runSpans);
        }
    }

    public void purgeRunV2(@NonNull Run run) {
        run.getActions(MonitoringAction.class).forEach(MonitoringAction::purgeSpan);
        if (run instanceof WorkflowRun) {
            WorkflowRun workflowRun = (WorkflowRun) run;
            List<FlowNode> flowNodesHeads = Optional.ofNullable(workflowRun.getExecution()).map(FlowExecution::getCurrentHeads).orElse(Collections.emptyList());
            for (FlowNode flowNodeHead : flowNodesHeads) {
                flowNodeHead.getActions(FlowNodeMonitoringAction.class).forEach(FlowNodeMonitoringAction::purgeSpan);
                flowNodeHead.getParents().forEach(flowNode -> flowNode.getActions(FlowNodeMonitoringAction.class).forEach(FlowNodeMonitoringAction::purgeSpan));
            }
        }
    }

    public void putSpan(@NonNull AbstractBuild build, @NonNull Span span) {
        RunIdentifier runIdentifier = RunIdentifier.fromBuild(build);
        FreestyleRunSpans runSpans = freestyleSpansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new FreestyleRunSpans());
        runSpans.runPhasesSpans.add(span);

        LOGGER.log(Level.FINEST, () -> "putSpan(" + build.getFullDisplayName() + "," + span + ") - new stack: " + runSpans);
    }

    public void putSpan(@NonNull Run run, @NonNull Span span) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = spansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new RunSpans());
        runSpans.runPhasesSpans.add(span);

        run.addAction(new MonitoringAction(span));

        LOGGER.log(Level.FINEST, () -> "putSpan(" + run.getFullDisplayName() + "," + span + ") - new stack: " + runSpans);
    }

    public void putSpan(@NonNull Run run, @NonNull Span span, @NonNull FlowNode flowNode) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = spansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new RunSpans());
        runSpans.pipelineStepSpansByFlowNodeId.put(flowNode.getId(), new PipelineSpanContext(span, flowNode));

        if (!flowNode.getActions(MonitoringAction.class).isEmpty()) {
            // should only happen for build agent allocation so we can track allocation duration
            LOGGER.log(Level.FINER, () ->
                "FlowNode[name: " + flowNode.getDisplayName() + ", function: " + flowNode.getDisplayFunctionName() + ", id: " + flowNode.getId() + "] " +
                    "is already associated with: " + flowNode.getActions(MonitoringAction.class).stream().map(ma -> "'" + ma.getSpanName() + "'").collect(Collectors.joining(",")));
        }
        flowNode.addAction(new FlowNodeMonitoringAction(span));

        LOGGER.log(Level.FINEST, () -> "putSpan(" + run.getFullDisplayName() + "," + " FlowNode[name: " + flowNode.getDisplayName() + ", function: " + flowNode.getDisplayFunctionName() + ", id: " + flowNode.getId() + "], Span[id: " + span.getSpanContext().getSpanId() + "]" + ") -  " + runSpans);
    }

    /**
     * @return If no span has been found (ie Jenkins restart), then the scope of a NoOp span is returned
     * @see #setupContext(Run, boolean)
     */
    @NonNull
    @MustBeClosed
    public Scope setupContext(@NonNull Run run) {
        return setupContext(run, true);
    }

    /**
     * @return If no span has been found (ie Jenkins restart), then the scope of a NoOp span is returned
     */
    @NonNull
    @MustBeClosed
    public Scope setupContext(@NonNull Run run, boolean verifyIfRemainingSteps) {
        Span span = getSpan(run, verifyIfRemainingSteps);
        return span.makeCurrent();
    }

    public Tracer getTracer() {
        return tracer;
    }

    @Override
    public void afterSdkInitialized(Meter meter, LoggerProvider loggerProvider, EventEmitter eventEmitter, Tracer tracer, ConfigProperties configProperties) {
        this.tracer = tracer;
    }

    @Override
    public void beforeSdkShutdown() {

    }

    @Immutable
    public static class RunSpans {
        final Multimap<String, PipelineSpanContext> pipelineStepSpansByFlowNodeId = ArrayListMultimap.create();

        /**
         * Root span and run phases spans
         */
        final List<Span> runPhasesSpans = new ArrayList<>();

        @Override
        public String toString() {
            // clone the Multimap to prevent a ConcurrentModificationException
            // see https://github.com/jenkinsci/opentelemetry-plugin/issues/129
            return "RunSpans{" +
                "runPhasesSpans=" + Collections.unmodifiableList(runPhasesSpans) +
                ", pipelineStepSpansByFlowNodeId=" + ArrayListMultimap.create(pipelineStepSpansByFlowNodeId) +
                '}';
        }
    }

    @Immutable
    public static class FreestyleRunSpans {
        final Multimap<String, FreestyleSpanContext> buildStepSpans = ArrayListMultimap.create();
        final List<Span> runPhasesSpans = new ArrayList<>();

        @Override
        public String toString() {
            // clone the Multimap to prevent a ConcurrentModificationException
            // see https://github.com/jenkinsci/opentelemetry-plugin/issues/129
            return "FreestyleRunSpans{" +
                "runPhasesSpans=" + Collections.unmodifiableList(runPhasesSpans) +
                ", buildStepSpans=" + ArrayListMultimap.create(buildStepSpans) +
                '}';
        }
    }

    public static class FreestyleSpanContext {
        final transient Span span;
        final String flowNodeId;

        public FreestyleSpanContext(@NonNull Span span, @NonNull BuildStep buildStep) {
            this.span = span;
            this.flowNodeId = buildStep.getClass().getSimpleName();
        }

        /**
         * FIXME handle cases where the data structure has been deserialized and {@link Span} is null.
         */
        @NonNull
        public Span getSpan() {
            return span;
        }

        @Override
        public String toString() {
            return "FreestyleSpanContext{" +
                "span=" + span +
                ", flowNodeId=" + flowNodeId +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FreestyleSpanContext that = (FreestyleSpanContext) o;
            return Objects.equals(this.span.getSpanContext().getSpanId(), that.span.getSpanContext().getSpanId()) && flowNodeId.equals(that.flowNodeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(span.getSpanContext().getSpanId(), flowNodeId);
        }
    }

    public static class PipelineSpanContext {
        final transient Span span;
        final String flowNodeId;
        final List<String> parentFlowNodeIds;

        public PipelineSpanContext(@NonNull Span span, @NonNull FlowNode flowNode) {
            this.span = span;
            this.flowNodeId = flowNode.getId();
            List<FlowNode> parents = flowNode.getParents();
            this.parentFlowNodeIds = new ArrayList<>(parents.size() + 1);
            this.parentFlowNodeIds.add(flowNode.getId());
            this.parentFlowNodeIds.addAll(parents.stream().map(FlowNode::getId).collect(Collectors.toList()));
        }

        /**
         * Return the stack of the parent {@link FlowNode} of this {@link Span}.
         * The first id of the list is the {@link FlowNode} on which the {@link Span} has been created, the last item of the list if the oldest parent.
         *
         * @see FlowNode#getParents()
         */
        @NonNull
        public List<String> getParentFlowNodeIds() {
            return parentFlowNodeIds;
        }

        /**
         * FIXME handle cases where the data structure has been deserialized and {@link Span} is null.
         */
        @NonNull
        public Span getSpan() {
            return span;
        }

        @Override
        public String toString() {
            return "PipelineSpanContext{" +
                "span=" + span +
                "flowNodeId=" + flowNodeId +
                ", parentIds=" + parentFlowNodeIds +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PipelineSpanContext that = (PipelineSpanContext) o;
            return Objects.equals(this.span.getSpanContext().getSpanId(), that.span.getSpanContext().getSpanId()) && flowNodeId.equals(that.flowNodeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(span.getSpanContext().getSpanId(), flowNodeId);
        }
    }

}
