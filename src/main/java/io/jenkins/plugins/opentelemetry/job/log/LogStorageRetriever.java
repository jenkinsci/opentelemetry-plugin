/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.console.AnnotatedLargeText;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

public interface LogStorageRetriever {

    String MESSAGE_KEY = "message";
    String ANNOTATIONS_KEY = "annotations";
    String POSITION_KEY = "position";
    String NOTE_KEY = "note";

    @Nonnull
    LogsQueryResult overallLog(@Nonnull String traceId, @Nonnull String spanId, @Nullable LogsQueryContext logsQueryContext) throws IOException;


    @Nonnull LogsQueryResult stepLog(@Nonnull String traceId, @Nonnull String spanId, @Nullable LogsQueryContext logsQueryContext) throws IOException;
}
