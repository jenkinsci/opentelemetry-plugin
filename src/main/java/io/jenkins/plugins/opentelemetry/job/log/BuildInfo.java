/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import com.google.common.base.Objects;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

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
    }

    @Override
    public String toString() {
        return "BuildInfo{" +
            "jobFullName='" + jobFullName + '\'' +
            ", runId='" + runNumber + '\'' +
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
}