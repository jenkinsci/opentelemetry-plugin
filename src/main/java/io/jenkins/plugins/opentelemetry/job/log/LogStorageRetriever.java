/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

public interface LogStorageRetriever<C extends LogsQueryContext> {

    String MESSAGE_KEY = "message";
    String ANNOTATIONS_KEY = "annotations";
    String POSITION_KEY = "position";
    String NOTE_KEY = "note";

    /**
     *
     * @param traceId
     * @param spanId
     * @param complete if true, we claim to be serving the complete log for a build, so implementations should be sure to retrieve final log lines
     * @param logsQueryContext
     */
    @Nonnull
    LogsQueryResult overallLog(@Nonnull String traceId, @Nonnull String spanId, boolean complete, @Nullable C logsQueryContext) throws IOException;


    @Nonnull LogsQueryResult stepLog(@Nonnull String traceId, @Nonnull String spanId, @Nullable C logsQueryContext) throws IOException;
}
