/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import com.google.common.base.Objects;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import edu.umd.cs.findbugs.annotations.NonNull;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RunTraceContext implements Serializable {

    static final long serialVersionUID = 1L;

    final String jobFullName;
    final int runNumber;
    final String spanId;
    final String traceId;
    /**
     * W3C Trace Context of the root span of the build
     *
     * @see Context
     */
    final Map<String, String> w3cTraceContext;

    /**
     * @param jobFullName     see {@link WorkflowJob#getFullName()}
     * @param runNumber       see {@link hudson.model.Run#getNumber()}
     * @param w3cTraceContext W3C Trace Context of the root span of the build
     */
    public RunTraceContext(String jobFullName, int runNumber, String traceId, String spanId, Map<String, String> w3cTraceContext) {
        this.jobFullName = jobFullName;
        this.runNumber = runNumber;
        this.traceId = traceId;
        this.spanId = spanId;
        this.w3cTraceContext = w3cTraceContext;
    }

    @NonNull
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

    public Map<String, String> getW3cTraceContext() {
        return Collections.unmodifiableMap(w3cTraceContext);
    }

    public Context getContext() {
        return W3CTraceContextPropagator.getInstance().extract(Context.current(), getW3cTraceContext(), new TextMapGetter<>() {
            @Override
            public Iterable<String> keys(@Nonnull Map<String, String> carrier) {
                return carrier.keySet();
            }

            @Nullable
            @Override
            public String get(@Nullable Map<String, String> carrier, @Nonnull String key) {
                assert carrier != null;
                return carrier.get(key);
            }
        });
    }

    @Override
    public String toString() {
        return "RunTraceContext{" +
            "jobFullName='" + jobFullName + '\'' +
            ", runNumber='" + runNumber + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RunTraceContext runTraceContext = (RunTraceContext) o;
        return runNumber == runTraceContext.runNumber &&
            Objects.equal(jobFullName, runTraceContext.jobFullName) &&
            Objects.equal(traceId, runTraceContext.traceId) &&
            Objects.equal(spanId, runTraceContext.spanId);
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

}