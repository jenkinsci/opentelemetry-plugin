/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.base.VerifyException;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.errorprone.annotations.MustBeClosed;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.tasks.BuildStep;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.RunContextKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Verify.verify;

@Extension
public class OtelTraceService {

    private static Logger LOGGER = Logger.getLogger(OtelTraceService.class.getName());

    private transient ConcurrentMap<RunIdentifier, RunSpans> spansByRun;

    private transient ConcurrentMap<RunIdentifier, FreestyleRunSpans> freestyleSpansByRun;

    private Tracer tracer;

    private Tracer noOpTracer;

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

    @Nonnull
    public Span getSpan(@Nonnull Run run) {
        return getSpan(run, true);
    }

    @Nonnull
    public Span getSpan(@Nonnull Run run, boolean verifyIfRemainingSteps) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = spansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new RunSpans()); // absent when Jenkins restarts during build

        if (verifyIfRemainingSteps) {
            verify(runSpans.pipelineStepSpansByFlowNodeId.isEmpty(), run.getFullDisplayName() + " - Can't access run phase span while there are remaining pipeline step spans: " + runSpans);
        }
        LOGGER.log(Level.FINEST, () -> "getSpan(" + run.getFullDisplayName() + ") - " + runSpans);
        final Span span = Iterables.getLast(runSpans.runPhasesSpans, null);
        if (span == null) {
            LOGGER.log(Level.FINE, () -> "No span found for run " + run.getFullDisplayName() + ", Jenkins server may have restarted");
            return noOpTracer.spanBuilder("noop-recovery-run-span-for-" + run.getFullDisplayName()).startSpan();
        }
        return span;
    }

    @Nonnull
    public Span getSpan(@Nonnull Run run, FlowNode flowNode) {

        RunIdentifier runIdentifier = OtelTraceService.RunIdentifier.fromRun(run);
        RunSpans runSpans = spansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new RunSpans()); // absent when Jenkins restarts during build
        LOGGER.log(Level.FINEST, () -> "getSpan(" + run.getFullDisplayName() + ", FlowNode[name" + flowNode.getDisplayName() + ", function:" + flowNode.getDisplayFunctionName() + ", id=" + flowNode.getId() + "]) -  " + runSpans);
        LOGGER.log(Level.FINEST, () -> "parentFlowNodes: " + flowNode.getParents().stream().map(node -> node.getDisplayName() + ", id: " + node.getId()).collect(Collectors.toList()));

        // TODO optimise lazy loading the list of ancestors just loading until w have found a span
        Iterable<FlowNode> ancestors = getAncestors(flowNode);
        for (FlowNode ancestor : ancestors) {
            final Collection<PipelineSpanContext> pipelineSpanContexts = runSpans.pipelineStepSpansByFlowNodeId.get(ancestor.getId());
            PipelineSpanContext pipelineSpanContext = Iterables.getLast(pipelineSpanContexts, null);
            if (pipelineSpanContext != null) {
                return pipelineSpanContext.getSpan();
            }
        }
        final Span span = Iterables.getLast(runSpans.runPhasesSpans, null);
        if (span == null) {
            LOGGER.log(Level.FINE, () -> "No span found for run " + run.getFullDisplayName() + ", Jenkins server may have restarted");
            return noOpTracer.spanBuilder("noop-recovery-run-span-for-" + run.getFullDisplayName()).startSpan();
        }
        LOGGER.log(Level.FINEST, () -> "getSpan(): " + span.getSpanContext().getSpanId());
        return span;
    }

    @Nonnull
    public Span getSpan(@Nonnull AbstractBuild build, @Nonnull BuildStep buildStep) {

        RunIdentifier runIdentifier = OtelTraceService.RunIdentifier.fromBuild(build);
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

    @Nonnull
    private Iterable<FlowNode> getAncestors(@Nonnull FlowNode flowNode) {
        // troubleshoot https://github.com/jenkinsci/opentelemetry-plugin/issues/197
        LOGGER.log(Level.FINEST, () -> "> getAncestors([" + flowNode.getClass().getSimpleName() + ", " + flowNode.getId() + ", '" + flowNode.getDisplayFunctionName() + "'])");
        List<FlowNode> ancestors = new ArrayList<>();
        buildListOfAncestors(flowNode, ancestors);
        // troubleshoot https://github.com/jenkinsci/opentelemetry-plugin/issues/197
        LOGGER.log(Level.FINEST, () -> "< getAncestors([" +  flowNode.getClass().getSimpleName() + ", " + flowNode.getId() + ", '" + flowNode.getDisplayFunctionName() + "']): " + ancestors.stream().map(fn -> "[" + fn.getId() + ", " + fn.getDisplayFunctionName() + "]").collect(Collectors.joining(", ")));
        return ancestors;
    }

    public void buildListOfAncestors(@Nonnull FlowNode flowNode, @Nonnull List<FlowNode> parents) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            // troubleshoot https://github.com/jenkinsci/opentelemetry-plugin/issues/197
            LOGGER.log(Level.FINEST, "buildListOfAncestors: [" + flowNode.getClass().getSimpleName() + ", " +
                flowNode.getId() + ", '" + flowNode.getDisplayFunctionName() + "']   -   " +
                parents.stream().map(fn -> "[" + fn.getId() + ", '" + fn.getDisplayFunctionName() + "']").collect(Collectors.joining(", ")));
        }
        parents.add(flowNode);
        if (flowNode instanceof StepEndNode) {
            StepEndNode endNode = (StepEndNode) flowNode;
            final StepStartNode startNode = endNode.getStartNode();
            parents.add(startNode);
            flowNode = startNode;
        }
        for (FlowNode parentNode : flowNode.getParents()) {
            if (flowNode.equals(parentNode)) {
                LOGGER.log(Level.INFO, "buildListOfAncestors(" + flowNode + "): skip parentFlowNode as it is the current node"); // TODO change message to Level.FINE once the cause is understood
            } else if (parents.contains(parentNode)) {
                LOGGER.log(Level.INFO, "buildListOfAncestors(" + flowNode + "): skip already added " + parentNode);  // TODO can we remove this check once the cause is understood?
            } else {
                buildListOfAncestors(parentNode, parents);
            }
        }
    }

    public void removePipelineStepSpan(@Nonnull Run run, @Nonnull FlowNode flowNode, @Nonnull Span span) {
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
        verify(removed == true, "%s - Failure to remove span %s for node %s: %s", run, pipelineSpanContext, startSpanNode, span, runSpans);

    }

    public void removeJobPhaseSpan(@Nonnull Run run, @Nonnull Span span) {
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

    public void removeBuildStepSpan(@Nonnull AbstractBuild build, @Nonnull BuildStep buildStep, @Nonnull Span span) {
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

    public void purgeRun(@Nonnull Run run) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = this.spansByRun.remove(runIdentifier);
        if (runSpans == null) {
            return;
        }

        if (!runSpans.runPhasesSpans.isEmpty() || !runSpans.pipelineStepSpansByFlowNodeId.isEmpty()) {
            throw new VerifyException(run.getFullDisplayName() + " - Some spans have not been ended and removed: " + runSpans);
        }
    }

    public void putSpan(@Nonnull AbstractBuild build, @Nonnull Span span) {
        RunIdentifier runIdentifier = RunIdentifier.fromBuild(build);
        FreestyleRunSpans runSpans = freestyleSpansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new FreestyleRunSpans());
        runSpans.runPhasesSpans.add(span);

        LOGGER.log(Level.FINEST, () -> "putSpan(" + build.getFullDisplayName() + "," + span + ") - new stack: " + runSpans);
    }

    public void putSpan(@Nonnull Run run, @Nonnull Span span) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = spansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new RunSpans());
        runSpans.runPhasesSpans.add(span);

        LOGGER.log(Level.FINEST, () -> "putSpan(" + run.getFullDisplayName() + "," + span + ") - new stack: " + runSpans);
    }

    public void putSpan(@Nonnull Run run, @Nonnull Span span, @Nonnull FlowNode flowNode) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = spansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new RunSpans());
        runSpans.pipelineStepSpansByFlowNodeId.put(flowNode.getId(), new PipelineSpanContext(span, flowNode));

        LOGGER.log(Level.FINEST, () -> "putSpan(" + run.getFullDisplayName() + "," + " FlowNode[name: " + flowNode.getDisplayName() + ", function: " + flowNode.getDisplayFunctionName() + ", id: " + flowNode.getId() + "], Span[id: " + span.getSpanContext().getSpanId() + "]" + ") -  " + runSpans);
    }

    @Inject
    public void setJenkinsOtelPlugin(@Nonnull OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.tracer = openTelemetrySdkProvider.getTracer();
        this.noOpTracer = TracerProvider.noop().get("jenkins");
    }

    /**
     * @return If no span has been found (ie Jenkins restart), then the scope of a NoOp span is returned
     */
    @Nonnull
    @MustBeClosed
    public Scope setupContext(@Nonnull Run run) {
        Span span = getSpan(run);
        Scope scope = span.makeCurrent();
        Context.current().with(RunContextKey.KEY, run);
        return scope;
    }

    public Tracer getTracer() {
        return tracer;
    }


    @Immutable
    public static class RunSpans {
        final Multimap<String, PipelineSpanContext> pipelineStepSpansByFlowNodeId = ArrayListMultimap.create();
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

        public FreestyleSpanContext(@Nonnull Span span, @Nonnull BuildStep buildStep) {
            this.span = span;
            this.flowNodeId = buildStep.getClass().getSimpleName();
        }

        /**
         * FIXME handle cases where the data structure has been deserialized and {@link Span} is null.
         */
        @Nonnull
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

        public PipelineSpanContext(@Nonnull Span span, @Nonnull FlowNode flowNode) {
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
        @Nonnull
        public List<String> getParentFlowNodeIds() {
            return parentFlowNodeIds;
        }

        /**
         * FIXME handle cases where the data structure has been deserialized and {@link Span} is null.
         */
        @Nonnull
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

    @Immutable
    public static class RunIdentifier implements Comparable<RunIdentifier> {
        final String jobName;
        final int runNumber;

        static RunIdentifier fromRun(@Nonnull Run run) {
            try {
                return new RunIdentifier(run.getParent().getFullName(), run.getNumber());
            } catch (StackOverflowError e) {
                // TODO remove when https://github.com/jenkinsci/opentelemetry-plugin/issues/87 is fixed
                try {
                    final String jobName = run.getParent().getName();
                    LOGGER.log(Level.WARNING, "Issue #87: StackOverflowError getting job name for " + jobName + "#" + run.getNumber());
                    return new RunIdentifier("#StackOverflowError#_" + jobName, run.getNumber());
                } catch (StackOverflowError e2) {
                    LOGGER.log(Level.WARNING, "Issue #87: StackOverflowError getting job name for unknown job #" + run.getNumber());
                    return new RunIdentifier("#StackOverflowError#", run.getNumber());
                }
            }
        }

        static RunIdentifier fromBuild(@Nonnull AbstractBuild build) {
            try {
                return new RunIdentifier(build.getParent().getFullName(), build.getNumber());
            } catch (StackOverflowError e) {
                // TODO remove when https://github.com/jenkinsci/opentelemetry-plugin/issues/87 is fixed
                try {
                    final String jobName = build.getParent().getName();
                    LOGGER.log(Level.WARNING, "Issue #87: StackOverflowError getting job name for " + jobName + "#" + build.getNumber());
                    return new RunIdentifier("#StackOverflowError#_" + jobName, build.getNumber());
                } catch (StackOverflowError e2) {
                    LOGGER.log(Level.WARNING, "Issue #87: StackOverflowError getting job name for unknown job #" + build.getNumber());
                    return new RunIdentifier("#StackOverflowError#", build.getNumber());
                }
            }
        }

        public RunIdentifier(@Nonnull String jobName, @Nonnull int runNumber) {
            this.jobName = jobName;
            this.runNumber = runNumber;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RunIdentifier that = (RunIdentifier) o;
            return runNumber == that.runNumber && jobName.equals(that.jobName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jobName, runNumber);
        }

        @Override
        public String toString() {
            return "RunIdentifier{" +
                    "jobName='" + jobName + '\'' +
                    ", runNumber=" + runNumber +
                    '}';
        }

        public String getJobName() {
            return jobName;
        }

        public int getRunNumber() {
            return runNumber;
        }

        @Override
        public int compareTo(RunIdentifier o) {
            return ComparisonChain.start().compare(this.jobName, o.jobName).compare(this.runNumber, o.runNumber).result();
        }
    }
}
