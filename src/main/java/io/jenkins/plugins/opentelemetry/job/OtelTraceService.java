package io.jenkins.plugins.opentelemetry.job;

import static com.google.common.base.Verify.*;

import com.google.common.base.VerifyException;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.MustBeClosed;
import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.RunContextKey;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Extension
public class OtelTraceService {

    private static Logger LOGGER = Logger.getLogger(OtelTraceService.class.getName());

    private transient ConcurrentMap<RunIdentifier, RunSpans> spansByRun;

    private Tracer tracer;

    public OtelTraceService() {
        initialize();
    }

    protected Object readResolve() {
        initialize();
        return this;
    }

    private void initialize() {
        spansByRun = new ConcurrentHashMap();
    }

    @CheckForNull
    public Span getSpan(@Nonnull Run run) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = this.spansByRun.get(runIdentifier);
        if (runSpans == null) {
            return null;
        }
        verify(runSpans.pipelineStepSpansByFlowNodeId.isEmpty(), run.getFullDisplayName() + " - Can't access run phase span while there are remaining pipeline step spans: " + runSpans);
        LOGGER.log(Level.FINER, () -> "getSpan(" + run.getFullDisplayName() + ") - " + runSpans);
        return Iterables.getLast(runSpans.runPhasesSpans, null);
    }

    @Nonnull
    public Span getSpan(@Nonnull Run run, FlowNode flowNode) {
        List<String> parentFlowNodeIds = flowNode.getParents().stream().map(FlowNode::getId).collect(Collectors.toList());
        List<String> flowNodesToEvaluate = new ArrayList<>(parentFlowNodeIds.size() + 1);
        flowNodesToEvaluate.add(flowNode.getId());
        flowNodesToEvaluate.addAll(parentFlowNodeIds);
        if (flowNode instanceof StepEndNode) {
            //  verify this logic, shouldn't we recurse?
            StepEndNode endNode = (StepEndNode) flowNode;
            StepStartNode startNode = endNode.getStartNode();
            flowNodesToEvaluate.add(startNode.getId());
            flowNodesToEvaluate.addAll(startNode.getParents().stream().map(FlowNode::getId).collect(Collectors.toList()));
        }

        RunIdentifier runIdentifier = OtelTraceService.RunIdentifier.fromRun(run);
        RunSpans runSpans = this.spansByRun.get(runIdentifier);
        if (runSpans == null) {
            LOGGER.log(Level.INFO, ()->"No span found for run " + run.getFullDisplayName() + ", Jenkins server may have restarted");
            RunSpans newRunSpans = new RunSpans();
            SpanBuilder rootSpanBuilder = getTracer().spanBuilder(run.getParent().getFullName() + "_recovered-after-restart" )
                    .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, run.getParent().getFullName())
                    .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_NAME, run.getParent().getFullDisplayName())
                    .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) run.getNumber());

            Span rootSpan = rootSpanBuilder.startSpan();
            newRunSpans.runPhasesSpans.add(rootSpan);
            this.spansByRun.put(runIdentifier, newRunSpans);

            Span runSpan = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.JENKINS_JOB_SPAN_PHASE_RUN_NAME + "_recovered-after-restart").setParent(Context.current().with(rootSpan)).startSpan();

            return runSpan;
        }
        LOGGER.log(Level.FINER, () -> "getSpan(" + run.getFullDisplayName() + ", " + flowNode + ") -  " + runSpans);

        for (String flowNodeId : flowNodesToEvaluate) {
            PipelineSpanContext pipelineSpanContext = runSpans.pipelineStepSpansByFlowNodeId.get(flowNodeId);
            if (pipelineSpanContext != null) {
                return pipelineSpanContext.getSpan();
            }
        }
        return Iterables.getLast(runSpans.runPhasesSpans);
    }

    public void removePipelineStepSpan(@Nonnull Run run, @Nonnull FlowNode flowNode, @Nonnull Span span) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = this.spansByRun.get(runIdentifier);

        FlowNode startSpanNode;
        if (flowNode instanceof AtomNode) {
            startSpanNode = flowNode;
        } else if (flowNode instanceof StepEndNode) {
            StepEndNode stepEndNode = (StepEndNode) flowNode;
            startSpanNode = stepEndNode.getStartNode();
        } else {
            throw new VerifyException("Can't remove span from node of type" + flowNode.getClass() + " - " + flowNode);
        }
        PipelineSpanContext pipelineSpanContext = runSpans.pipelineStepSpansByFlowNodeId.remove(startSpanNode.getId());
        verifyNotNull(pipelineSpanContext,"%s - No span found for node %s: %s", run, startSpanNode, span, runSpans);
    }

    public void removeJobPhaseSpan(@Nonnull Run run, @Nonnull Span span) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = this.spansByRun.get(runIdentifier);

        verify(runSpans.pipelineStepSpansByFlowNodeId.isEmpty(), "%s - Try to remove span associated with a run phase even though there are remain spans associated with flow nodes: %s", run, runSpans);

        Span lastSpan = Iterables.getLast(runSpans.runPhasesSpans, null);
        if (Objects.equals(span, lastSpan)) {
            boolean removed = runSpans.runPhasesSpans.remove(span);
            verify(removed, run.getFullDisplayName() + "Failure to remove span from runPhasesSpans: " + span);
            return;
        }

        throw new VerifyException(run.getFullDisplayName() + " - Failure to remove span " + span + " - " + runSpans);
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

    public void putSpan(@Nonnull Run run, @Nonnull Span span) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = spansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new RunSpans());
        runSpans.runPhasesSpans.add(span);

        LOGGER.log(Level.FINER, () -> "putSpan(" + run.getFullDisplayName() + "," + span + ") - new stack: " + runSpans);
    }

    public void putSpan(@Nonnull Run run, @Nonnull Span span, @Nonnull FlowNode flowNode) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        RunSpans runSpans = spansByRun.computeIfAbsent(runIdentifier, runIdentifier1 -> new RunSpans());
        runSpans.pipelineStepSpansByFlowNodeId.put(flowNode.getId(), new PipelineSpanContext(span, flowNode));

        LOGGER.log(Level.FINER, () -> "putSpan(" + run.getFullDisplayName() + "," + span + ") -  " + runSpans);
    }

    @Inject
    public void setJenkinsOtelPlugin(@Nonnull OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.tracer = openTelemetrySdkProvider.getTracer();
    }

    /**
     * @param run
     * @return {@code null} if no {@link Span} has been created for the given {@link Run}
     */
    @CheckForNull
    @MustBeClosed
    public Scope setupContext(@Nonnull Run run) {
        Span span = getSpan(run);
        if (span == null) {
            return null;
        } else {
            Scope scope = span.makeCurrent();
            Context.current().with(RunContextKey.KEY, run);
            return scope;
        }
    }

    public Tracer getTracer() {
        return tracer;
    }


    @Immutable
    public static class RunSpans {
        final Map<String, PipelineSpanContext> pipelineStepSpansByFlowNodeId = new HashMap<>();
        final List<Span> runPhasesSpans = new ArrayList<>();

        @Override
        public String toString() {
            return "RunSpans{" +
                    "runPhasesSpans=" + runPhasesSpans +
                    ", pipelineStepSpansByFlowNodeId=" + pipelineStepSpansByFlowNodeId +
                    '}';
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
    }

    @Immutable
    public static class RunIdentifier implements Comparable<RunIdentifier> {
        final String jobName;
        final int runNumber;

        static RunIdentifier fromRun(@Nonnull Run run) {
            return new RunIdentifier(run.getParent().getFullName(), run.getNumber());
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
