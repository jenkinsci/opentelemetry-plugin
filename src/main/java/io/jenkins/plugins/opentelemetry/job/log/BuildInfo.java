/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import com.google.common.base.Objects;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryTracesSemanticConventions.SPAN_ID;
import static io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryTracesSemanticConventions.TRACE_ID;

public final class BuildInfo implements Serializable {

    static final long serialVersionUID = 1L;

    final String jobFullName;
    final int runNumber;
    final String spanId;
    final String traceId;
    /**
     * W3C Trace Context of the root span of the build
     * @see Context
     */
    @Deprecated
    final Map<String, String> w3cTraceContext;

    /**
     *
     * @param jobFullName see {@link WorkflowJob#getFullName()}
     * @param runNumber see {@link hudson.model.Run#getNumber()}
     * @param w3cTraceContext W3C Trace Context of the root span of the build
     */
    public BuildInfo(String jobFullName, int runNumber,String traceId, String spanId, Map<String, String> w3cTraceContext) {
        this.jobFullName = jobFullName;
        this.runNumber = runNumber;
        this.traceId = traceId;
        this.spanId = spanId;
        this.w3cTraceContext = w3cTraceContext;
    }

    public BuildInfo(BuildInfo buildInfo) {
        this.jobFullName = buildInfo.jobFullName;
        this.runNumber = buildInfo.runNumber;
        this.traceId = buildInfo.traceId;
        this.spanId = buildInfo.spanId;
        this.w3cTraceContext = new HashMap<>(buildInfo.w3cTraceContext);
    }

    @Nonnull
    public Attributes toAttributes() {
        return Attributes.builder()
            .put(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, jobFullName)
            .put(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, runNumber)
            .build();
    }

    public String getJobFullName() {
        return jobFullName;
    }

    public int getRunNumber() {
        return runNumber;
    }

    /**
     * Can we remove this?
     */
    @Deprecated
    public Map<String, String> getW3cTraceContext() {
        return Collections.unmodifiableMap(w3cTraceContext);
    }

    @Override
    public String toString() {
        return "BuildInfo{" +
            "jobFullName='" + jobFullName + '\'' +
            ", runNumber='" + runNumber + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuildInfo buildInfo = (BuildInfo) o;
        return runNumber == buildInfo.runNumber && Objects.equal(jobFullName, buildInfo.jobFullName) && Objects.equal(w3cTraceContext, buildInfo.w3cTraceContext);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(jobFullName, runNumber, w3cTraceContext);
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setFlowNode(String flowNodeId) {
        if(w3cTraceContext != null){
            w3cTraceContext.put("flowNode", flowNodeId);
        }
    }
}