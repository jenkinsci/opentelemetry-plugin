/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

public interface LogStorageRetriever extends AutoCloseable {

    /**
     * @param  jobFullName see {@link hudson.model.AbstractItem#getFullName()}
     * @param runNumber see {@link hudson.model.Run#getNumber()}
     * @param complete if true, we claim to be serving the complete log for a build, so implementations should be sure to retrieve final log lines
     */
    @NonNull
    LogsQueryResult overallLog(@NonNull String jobFullName, @NonNull int runNumber, @NonNull String traceId, @NonNull String spanId, boolean complete) throws IOException;


    /**
     * @param jobFullName see {@link hudson.model.AbstractItem#getFullName()}
     * @param runNumber see {@link hudson.model.Run#getNumber()}
     * @param flowNodeId see {@link org.jenkinsci.plugins.workflow.graph.FlowNode#getId()}
     * @param complete if true, we claim to be serving the complete log for a build, so implementations should be sure to retrieve final log lines
     */
    @NonNull LogsQueryResult stepLog(@NonNull String jobFullName, @NonNull int runNumber, @NonNull String flowNodeId, @NonNull String traceId, @NonNull String spanId, boolean complete) throws IOException;

}
