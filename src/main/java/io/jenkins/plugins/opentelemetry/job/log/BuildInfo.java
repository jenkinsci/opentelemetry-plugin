/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;

import javax.annotation.Nullable;
import java.util.Map;

final class BuildInfo {
    final String jobFullName;
    final int runNumber;
    /**
     * @see Context
     */
    final Map<String, String> context;

    BuildInfo(String jobFullName, int runNumber, Map<String, String> context) {
        this.jobFullName = jobFullName;
        this.runNumber = runNumber;
        this.context = context;
    }

    Attributes toAttributes(){
        return Attributes.builder()
            .put(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, jobFullName)
            .put(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, runNumber)
            .build();
    }

    Context toContext(){
        return W3CTraceContextPropagator.getInstance().extract(Context.current(), this.context, new TextMapGetter<Map<String, String>>() {
            @Override
            public Iterable<String> keys(Map<String, String> carrier) {
                return carrier.keySet();
            }

            @Nullable
            @Override
            public String get(@Nullable Map<String, String> carrier, String key) {
                return carrier == null ? null : carrier.get(key);
            }
        });
    }

    @Override
    public String toString() {
        return "BuildInfo{" +
            "jobFullName='" + jobFullName + '\'' +
            ", runId='" + runNumber + '\'' +
            '}';
    }
}
