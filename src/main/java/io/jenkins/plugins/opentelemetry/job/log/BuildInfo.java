/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;

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

    @Override
    public String toString() {
        return "BuildInfo{" +
            "jobFullName='" + jobFullName + '\'' +
            ", runId='" + runNumber + '\'' +
            '}';
    }
}
