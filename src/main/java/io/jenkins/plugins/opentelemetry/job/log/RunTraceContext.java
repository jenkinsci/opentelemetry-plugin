/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import com.google.common.base.Objects;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

public class RunTraceContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
    public RunTraceContext(
            String jobFullName, int runNumber, String traceId, String spanId, Map<String, String> w3cTraceContext) {
        this.jobFullName = jobFullName;
        this.runNumber = runNumber;
        this.traceId = traceId;
        this.spanId = spanId;
        this.w3cTraceContext = Collections.unmodifiableMap(w3cTraceContext);
    }

    /**
     * Converts this trace context into OpenTelemetry {@link Attributes} containing the pipeline
     * name and run number.
     *
     * @return attributes for the associated run
     */
    @NonNull
    public Attributes toAttributes() {
        return Attributes.builder()
                .put(ExtendedJenkinsAttributes.CI_PIPELINE_ID, jobFullName)
                .put(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_NUMBER, runNumber)
                .build();
    }

    /**
     * Returns the full name of the Jenkins job.
     *
     * @return job full name
     */
    public String getJobFullName() {
        return jobFullName;
    }

    /**
     * Returns the build run number.
     *
     * @return run number
     */
    public int getRunNumber() {
        return runNumber;
    }

    /**
     * @return unmodifiable W3C Trace Context
     */
    public Map<String, String> getW3cTraceContext() {
        return w3cTraceContext;
    }

    /**
     * Extracts the OpenTelemetry {@link Context} from the stored W3C trace context map.
     *
     * @return the extracted context
     */
    public Context getContext() {
        return W3CTraceContextPropagator.getInstance()
                .extract(Context.current(), getW3cTraceContext(), new TextMapGetter<>() {
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

    /**
     * Returns a string representation of this trace context.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "RunTraceContext{" + "jobFullName='"
                + jobFullName + '\'' + ", runNumber="
                + runNumber + ", spanId='"
                + spanId + '\'' + ", traceId='"
                + traceId + '\'' + '}';
    }

    /**
     * Compares this context by job name and run number.
     *
     * @param o object to compare
     * @return {@code true} when both represent the same run
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RunTraceContext runTraceContext = (RunTraceContext) o;
        return runNumber == runTraceContext.runNumber
                && Objects.equal(jobFullName, runTraceContext.jobFullName)
                && Objects.equal(traceId, runTraceContext.traceId)
                && Objects.equal(spanId, runTraceContext.spanId);
    }

    /**
     * Returns hash code based on job name and run number.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(jobFullName, runNumber, traceId, spanId);
    }

    /**
     * Returns the OTel trace ID for the root span of this run.
     *
     * @return trace ID
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * Returns the OTel span ID for this run.
     *
     * @return span ID
     */
    public String getSpanId() {
        return spanId;
    }
}
