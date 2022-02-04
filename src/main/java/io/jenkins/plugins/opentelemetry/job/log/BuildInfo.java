/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import com.google.common.base.Objects;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;

import static io.jenkins.plugins.opentelemetry.semconv.OTelEnvironmentVariablesConventions.SPAN_ID;
import static io.jenkins.plugins.opentelemetry.semconv.OTelEnvironmentVariablesConventions.TRACE_ID;

public final class BuildInfo {
    final String jobFullName;
    final int runNumber;
    /**
     * @see Context
     */
    final Map<String, String> context;

    public BuildInfo(String jobFullName, int runNumber, Map<String, String> context) {
        this.jobFullName = jobFullName;
        this.runNumber = runNumber;
        this.context = context;
    }

    @Nonnull
    public Attributes toAttributes() {
        return Attributes.builder()
            .put(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, jobFullName)
            .put(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, runNumber)
            .build();
        //FIXME should we add the context as attributes?
    }

    public String getJobFullName() {
        return jobFullName;
    }

    public int getRunNumber() {
        return runNumber;
    }

    public Map<String, String> getContext() {
        return Collections.unmodifiableMap(context);
    }

    @Override
    public String toString() {
        return "BuildInfo{" +
            "jobFullName='" + jobFullName + '\'' +
            ", runId='" + runNumber + '\'' +
            ", context='" + context != null ? context.toString() : "NA" + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuildInfo buildInfo = (BuildInfo) o;
        return runNumber == buildInfo.runNumber && Objects.equal(jobFullName, buildInfo.jobFullName) && Objects.equal(context, buildInfo.context);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(jobFullName, runNumber, context);
    }

    @CheckForNull
    public String getTraceId() {
        return getContextKey(TRACE_ID);
    }

    @CheckForNull
    public String getSpanId() {
        return getContextKey(SPAN_ID);
    }

    @Nullable
    private String getContextKey(@Nonnull String key) {
        String ret = null;
        if (context != null) {
            ret = context.get(key);
        }
        return ret;
    }

    public void setFlowNode(String flowNodeId) {
        if(context != null){
            context.put("flowNode", flowNodeId);
        }
    }
}