/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.Nonnull;
import java.util.Map;

public class FlowNodeTraceContext extends RunTraceContext {

    public static FlowNodeTraceContext newFlowNodeTraceContext(@NonNull Run run, @NonNull FlowNode flowNode, @NonNull Span span) {
        String spanId = span.getSpanContext().getSpanId();
        String traceId = span.getSpanContext().getTraceId();
        Map<String, String> w3cTraceContext = OtelUtils.getW3cTraceContext(span);
        return new FlowNodeTraceContext(run.getFullDisplayName(), run.getNumber(), flowNode.getId(), traceId, spanId, w3cTraceContext);
    }

    private final String flowNodeId;

    public FlowNodeTraceContext(String jobFullName, int runNumber, String flowNodeId, String traceId, String spanId, Map<String, String> w3cTraceContext) {
        super(jobFullName, runNumber, traceId, spanId, w3cTraceContext);
        this.flowNodeId = flowNodeId;
    }

    public String getFlowNodeId() {
        return flowNodeId;
    }

    @Override
    public String toString() {
        return "FlowNodeTraceContext{" +
            "flowNodeId='" + flowNodeId + '\'' +
            ", jobFullName='" + jobFullName + '\'' +
            ", runNumber=" + runNumber +
            ", spanId='" + spanId + '\'' +
            ", traceId='" + traceId + '\'' +
            ", w3cTraceContext=" + w3cTraceContext +
            '}';
    }

    @Nonnull
    @Override
    public Attributes toAttributes() {
        return Attributes.builder()
            .putAll(super.toAttributes())
            .put(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, flowNodeId)
            .build();
    }
}
