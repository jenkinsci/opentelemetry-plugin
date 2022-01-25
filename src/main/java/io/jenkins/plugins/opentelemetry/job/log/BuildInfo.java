/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;

final class BuildInfo {
    final String jobFullName;
    final String runId;

    BuildInfo(String jobFullName, String runId) {
        this.jobFullName = jobFullName;
        this.runId = runId;
    }

    Attributes toAttributes(){
        return Attributes.builder()
            .put(JenkinsOtelSemanticAttributes.CI_PIPELINE_NAME, jobFullName)
            .put(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, runId)
            .build();
    }

    @Override
    public String toString() {
        return "BuildInfo{" +
            "jobFullName='" + jobFullName + '\'' +
            ", runId='" + runId + '\'' +
            '}';
    }
}
