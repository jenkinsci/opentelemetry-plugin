/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

public interface LogStorageRetriever<C extends LogsQueryContext> {

    /**
     * TODO also pass {@link io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes#CI_PIPELINE_ID} and {@link io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes#CI_PIPELINE_RUN_NUMBER}
     * @param  jobFullName see {@link hudson.model.AbstractItem#getFullName()}
     * @param runNumber see {@link hudson.model.Run#getNumber()}
     * @param complete if true, we claim to be serving the complete log for a build, so implementations should be sure to retrieve final log lines
     */
    @Nonnull
    LogsQueryResult overallLog(@Nonnull String jobFullName, @Nonnull int runNumber, @Nonnull String traceId, @Nonnull String spanId, boolean complete, @Nullable C logsQueryContext) throws IOException;


    /**
     * TODO also pass {@link io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes#CI_PIPELINE_ID}, {@link io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes#CI_PIPELINE_RUN_NUMBER}, {@link io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes#JENKINS_STEP_ID}
     * @param jobFullName see {@link hudson.model.AbstractItem#getFullName()}
     * @param runNumber see {@link hudson.model.Run#getNumber()}
     * @param flowNodeId see {@link org.jenkinsci.plugins.workflow.graph.FlowNode#getId()}
     */
    @Nonnull LogsQueryResult stepLog(@Nonnull String jobFullName, @Nonnull int runNumber, @Nonnull String flowNodeId, @Nonnull String traceId, @Nonnull String spanId, @Nullable C logsQueryContext) throws IOException;
}
