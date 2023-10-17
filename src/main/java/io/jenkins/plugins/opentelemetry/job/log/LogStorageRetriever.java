/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Run;

import java.io.IOException;
import java.time.Instant;

public interface LogStorageRetriever extends AutoCloseable {

    /**
     * @param jobFullName see {@link hudson.model.AbstractItem#getFullName()}
     * @param runNumber   see {@link hudson.model.Run#getNumber()}
     * @param complete    if true, we claim to be serving the complete log for a build, so implementations should be sure to retrieve final log lines
     * @param startTime   Pipeline run start time. See {@link Run#getStartTimeInMillis()}
     * @param endTime    {@code null} if the pipeline is still running. See {@link Run#getDuration()}
     */
    @NonNull
    LogsQueryResult overallLog(@NonNull String jobFullName, int runNumber, @NonNull String traceId, @NonNull String spanId, boolean complete, @NonNull Instant startTime, @Nullable Instant endTime) throws IOException;


    /**
     * @param jobFullName see {@link hudson.model.AbstractItem#getFullName()}
     * @param runNumber   see {@link hudson.model.Run#getNumber()}
     * @param flowNodeId  see {@link org.jenkinsci.plugins.workflow.graph.FlowNode#getId()}
     * @param complete    if true, we claim to be serving the complete log for a build, so implementations should be sure to retrieve final log lines
     * @param startTime   Pipeline run start time. See {@link Run#getStartTimeInMillis()}
     * @param endTime    {@code null} if the pipeline is still running. See {@link Run#getDuration()}
     */
    @NonNull LogsQueryResult stepLog(@NonNull String jobFullName, int runNumber, @NonNull String flowNodeId, @NonNull String traceId, @NonNull String spanId, boolean complete, @NonNull Instant startTime, @Nullable Instant endTime) throws IOException;

}
