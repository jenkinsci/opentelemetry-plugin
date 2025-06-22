/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import com.google.common.base.Objects;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import java.io.Serial;
import java.util.Map;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

public class FlowNodeTraceContext extends RunTraceContext {

    @Serial
    private static final long serialVersionUID = 1L;

    public static FlowNodeTraceContext newFlowNodeTraceContext(
            @NonNull Run run, @NonNull FlowNode flowNode, @NonNull Span span) {
        String spanId = span.getSpanContext().getSpanId();
        String traceId = span.getSpanContext().getTraceId();
        Map<String, String> w3cTraceContext = OtelUtils.getW3cTraceContext(span);
        return new FlowNodeTraceContext(
                run.getParent().getFullName(), run.getNumber(), flowNode.getId(), traceId, spanId, w3cTraceContext);
    }

    private final String flowNodeId;

    public FlowNodeTraceContext(
            String jobFullName,
            int runNumber,
            String flowNodeId,
            String traceId,
            String spanId,
            Map<String, String> w3cTraceContext) {
        super(jobFullName, runNumber, traceId, spanId, w3cTraceContext);
        this.flowNodeId = flowNodeId;
    }

    public String getFlowNodeId() {
        return flowNodeId;
    }

    @Override
    public String toString() {
        return "FlowNodeTraceContext{" + "jobFullName='"
                + jobFullName + '\'' + ", runNumber="
                + runNumber + ", flowNodeId='"
                + flowNodeId + '\'' + ", spanId='"
                + spanId + '\'' + ", traceId='"
                + traceId + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlowNodeTraceContext traceContext = (FlowNodeTraceContext) o;
        return runNumber == traceContext.runNumber
                && Objects.equal(jobFullName, traceContext.jobFullName)
                && Objects.equal(flowNodeId, traceContext.flowNodeId)
                && Objects.equal(traceId, traceContext.traceId)
                && Objects.equal(spanId, traceContext.spanId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), flowNodeId);
    }

    @Nonnull
    @Override
    public Attributes toAttributes() {
        return Attributes.builder()
                .putAll(super.toAttributes())
                .put(ExtendedJenkinsAttributes.JENKINS_STEP_ID, flowNodeId)
                .build();
    }
}
