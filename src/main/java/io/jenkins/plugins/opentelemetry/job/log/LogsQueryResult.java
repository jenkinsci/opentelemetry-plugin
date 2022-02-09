/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import java.nio.charset.Charset;

public class LogsQueryResult {
    public LogsQueryResult(@Nonnull ByteBuffer byteBuffer, @Nonnull Charset charset, boolean complete, @Nonnull LogsQueryContext newLogsQueryContext) {
        this.byteBuffer = byteBuffer;
        this.charset = charset;
        this.complete = complete;
        this.logsQueryContext = newLogsQueryContext;
    }

    final ByteBuffer byteBuffer;
    final Charset charset;
    final boolean complete;
    final LogsQueryContext logsQueryContext;

    @Nonnull
    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    @Nonnull
    public Charset getCharset() {
        return charset;
    }

    @Nonnull
    public boolean isComplete() {
        return complete;
    }

    @Nonnull
    public LogsQueryContext getLogsQueryContext() {
        return logsQueryContext;
    }
}
