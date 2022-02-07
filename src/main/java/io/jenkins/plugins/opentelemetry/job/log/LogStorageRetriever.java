/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import java.io.IOException;

public interface LogStorageRetriever {

    String MESSAGE_KEY = "message";
    String ANNOTATIONS_KEY = "annotations";
    String POSITION_KEY = "position";
    String NOTE_KEY = "note";

    @Nonnull
    ByteBuffer overallLog(@Nonnull String traceId, @Nonnull String spanId) throws IOException;


    @Nonnull ByteBuffer stepLog(@Nonnull String traceId, @Nonnull String spanId) throws IOException;
}
