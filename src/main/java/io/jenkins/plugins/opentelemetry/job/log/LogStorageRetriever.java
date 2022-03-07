/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import javax.annotation.Nonnull;
import java.io.IOException;

public interface LogStorageRetriever {

    /**
     * @param  jobFullName see {@link hudson.model.AbstractItem#getFullName()}
     * @param runNumber see {@link hudson.model.Run#getNumber()}
     * @param complete if true, we claim to be serving the complete log for a build, so implementations should be sure to retrieve final log lines
     */
    @Nonnull
    LogsQueryResult overallLog(@Nonnull String jobFullName, @Nonnull int runNumber, @Nonnull String traceId, @Nonnull String spanId, boolean complete) throws IOException;


    /**
     * @param jobFullName see {@link hudson.model.AbstractItem#getFullName()}
     * @param runNumber see {@link hudson.model.Run#getNumber()}
     * @param flowNodeId see {@link org.jenkinsci.plugins.workflow.graph.FlowNode#getId()}
     * @param complete if true, we claim to be serving the complete log for a build, so implementations should be sure to retrieve final log lines
     */
    @Nonnull LogsQueryResult stepLog(@Nonnull String jobFullName, @Nonnull int runNumber, @Nonnull String flowNodeId, @Nonnull String traceId, @Nonnull String spanId, boolean complete) throws IOException;

}
