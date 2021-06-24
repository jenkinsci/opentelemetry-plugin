/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.errorprone.annotations.MustBeClosed;
import hudson.Extension;
import hudson.slaves.NodeProvisioner;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.computer.opentelemetry.context.PlannedNodeContextKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

import static com.google.common.base.Verify.verify;

@Extension
public class OtelTraceService {

    private static Logger LOGGER = Logger.getLogger(OtelTraceService.class.getName());

    private transient ConcurrentMap<PlannedNodeIdentifier, CloudSpans> spansByCloud;

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
        spansByCloud = new ConcurrentHashMap();
    }

    @Nonnull
    public Span getSpan(@Nonnull NodeProvisioner.PlannedNode plannedNode) {
        return getSpan(plannedNode, true);
    }

    @Nonnull
    public Span getSpan(@Nonnull NodeProvisioner.PlannedNode plannedNode, boolean verifyIfRemainingSteps) {
        PlannedNodeIdentifier plannedNodeIdentifier = PlannedNodeIdentifier.fromRun(plannedNode);
        CloudSpans cloudSpans = spansByCloud.computeIfAbsent(plannedNodeIdentifier, plannedNodeIdentifier1 -> new CloudSpans()); // absent when Jenkins restarts during build

        if (verifyIfRemainingSteps) {
            verify(cloudSpans.cloudSpansByFlowNodeId.isEmpty(), plannedNode.displayName + " - Can't access run phase span while there are remaining cloud step spans: " + cloudSpans);
        }
        LOGGER.log(Level.FINEST, () -> "getSpan(" + plannedNode.displayName + ") - " + cloudSpans);
        final Span span = Iterables.getLast(cloudSpans.runPhasesSpans, null);
        if (span == null) {
            LOGGER.log(Level.FINE, () -> "No span found for run " + plannedNode.displayName+ ", Jenkins server may have restarted");
            return noOpTracer.spanBuilder("noop-recovery-planned-node-span-for-" + plannedNode.displayName).startSpan();
        }
        return span;
    }

    @Nonnull
    public Span getSpan(@Nonnull NodeProvisioner.PlannedNode plannedNode, hudson.model.Node flowNode) {
        PlannedNodeIdentifier plannedNodeIdentifier = PlannedNodeIdentifier.fromRun(plannedNode);
        CloudSpans cloudSpans = spansByCloud.computeIfAbsent(plannedNodeIdentifier, plannedNodeIdentifier1 -> new CloudSpans()); // absent when Jenkins restarts during build
        LOGGER.log(Level.FINEST, () -> "getSpan(" + plannedNode.displayName + ", Node[displayName" + flowNode.getDisplayName() + ", nodeName:" + flowNode.getNodeName() + ", label=" + flowNode.getLabelString() + "]) -  " + cloudSpans);

        final Span span = Iterables.getLast(cloudSpans.runPhasesSpans, null);
        if (span == null) {
            LOGGER.log(Level.FINE, () -> "No span found for run " + plannedNode.displayName + ", Jenkins server may have restarted");
            return noOpTracer.spanBuilder("noop-recovery-planned-node-span-for-" + plannedNode.displayName).startSpan();
        }
        LOGGER.log(Level.FINEST, () -> "span: " + span.getSpanContext().getSpanId());
        return span;
    }

    public void putSpan(@Nonnull NodeProvisioner.PlannedNode plannedNode, @Nonnull Span span) {
        PlannedNodeIdentifier plannedNodeIdentifier = PlannedNodeIdentifier.fromRun(plannedNode);
        CloudSpans cloudSpans = spansByCloud.computeIfAbsent(plannedNodeIdentifier, plannedNodeIdentifier1 -> new CloudSpans());
        cloudSpans.runPhasesSpans.add(span);

        LOGGER.log(Level.FINEST, () -> "putSpan(" + plannedNode.displayName + "," + span + ") - new stack: " + cloudSpans);
    }

    public void putSpan(@Nonnull NodeProvisioner.PlannedNode plannedNode, @Nonnull Span span, @Nonnull hudson.model.Node flowNode) {
        PlannedNodeIdentifier plannedNodeIdentifier = PlannedNodeIdentifier.fromRun(plannedNode);
        CloudSpans cloudSpans = spansByCloud.computeIfAbsent(plannedNodeIdentifier, plannedNodeIdentifier1 -> new CloudSpans());
        cloudSpans.cloudSpansByFlowNodeId.put(flowNode.getDisplayName(), new CloudSpanContext(span, flowNode));

        LOGGER.log(Level.FINEST, () -> "putSpan(" + plannedNode.displayName + ", Node[displayName" + flowNode.getDisplayName() + ", nodeName:" + flowNode.getNodeName() + ", label=" + flowNode.getLabelString() + "], Span[id: " + span.getSpanContext().getSpanId() + "]" + ") -  " + cloudSpans);
    }

    @Inject
    public void setJenkinsOtelPlugin(@Nonnull OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.tracer = openTelemetrySdkProvider.getTracer();
        this.noOpTracer = TracerProvider.noop().get("jenkins");
    }

    /**
     * @param plannedNodes
     * @return If no span has been found (ie Jenkins restart), then the scope of a NoOp span is returned
     */
    @Nonnull
    @MustBeClosed
    public Scope setupContext(@Nonnull Collection<NodeProvisioner.PlannedNode> plannedNodes) {
        if (plannedNodes.size() == 1) {
            return setupContext(plannedNodes.iterator().next());
        }
        // NOOP span . TODO
        return null;
    }

    /**
     * @param plannedNode
     * @return If no span has been found (ie Jenkins restart), then the scope of a NoOp span is returned
     */
    @Nonnull
    @MustBeClosed
    public Scope setupContext(@Nullable NodeProvisioner.PlannedNode plannedNode) {
        if (plannedNode != null) {
            Span span = getSpan(plannedNode);
            Scope scope = span.makeCurrent();
            Context.current().with(PlannedNodeContextKey.KEY, plannedNode);
            return scope;
        }
        // NOOP span . TODO
        return null;
    }

    public Tracer getTracer() {
        return tracer;
    }


    @Immutable
    public static class CloudSpans {
        final Multimap<String, CloudSpanContext> cloudSpansByFlowNodeId = ArrayListMultimap.create();
        final List<Span> runPhasesSpans = new ArrayList<>();

        @Override
        public String toString() {
            // clone the Multimap to prevent a ConcurrentModificationException
            // see https://github.com/jenkinsci/opentelemetry-plugin/issues/129
            return "CloudSpans{" +
                    "runPhasesSpans=" + Collections.unmodifiableList(runPhasesSpans) +
                    ", pipelineStepSpansByFlowNodeId=" + ArrayListMultimap.create(cloudSpansByFlowNodeId) +
                    '}';
        }
    }

    public static class CloudSpanContext {
        final transient Span span;
        final String flowNodeId;
        final List<String> parentFlowNodeIds;

        public CloudSpanContext(@Nonnull Span span, @Nonnull hudson.model.Node flowNode) {
            this.span = span;
            this.flowNodeId = flowNode.getNodeName();
            this.parentFlowNodeIds = new ArrayList<>( 1);
            this.parentFlowNodeIds.add(flowNode.getNodeName());
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
            return "CloudSpanContext{" +
                    "span=" + span +
                    "flowNodeId=" + flowNodeId +
                    ", parentIds=" + parentFlowNodeIds +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CloudSpanContext that = (CloudSpanContext) o;
            return Objects.equals(this.span.getSpanContext().getSpanId(), that.span.getSpanContext().getSpanId()) && flowNodeId.equals(that.flowNodeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(span.getSpanContext().getSpanId(), flowNodeId);
        }
    }

    @Immutable
    public static class PlannedNodeIdentifier implements Comparable<PlannedNodeIdentifier> {
        final String nodeName;

        static PlannedNodeIdentifier fromRun(@Nonnull NodeProvisioner.PlannedNode run) {
            return new PlannedNodeIdentifier(run.displayName);
        }

        public PlannedNodeIdentifier(@Nonnull String nodeName) {
            this.nodeName = nodeName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PlannedNodeIdentifier that = (PlannedNodeIdentifier) o;
            return nodeName.equals(that.nodeName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeName);
        }

        @Override
        public String toString() {
            return "PlannedNodeIdentifier{" +
                    "nodeName='" + nodeName + '\'' +
                    '}';
        }

        public String getNodeName() {
            return nodeName;
        }


        @Override
        public int compareTo(PlannedNodeIdentifier o) {
            return ComparisonChain.start().compare(this.nodeName, o.nodeName).result();
        }
    }
}
