/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import java.nio.charset.Charset;

public class LogsQueryResult {
    public LogsQueryResult(@Nonnull ByteBuffer byteBuffer, @Nonnull LogsViewHeader logsViewHeader, @Nonnull Charset charset, boolean completed, @Nonnull LogsQueryContext newLogsQueryContext) {
        this.byteBuffer = byteBuffer;
        this.logsViewHeader = logsViewHeader;
        this.charset = charset;
        this.complete = completed;
        this.logsQueryContext = newLogsQueryContext;
    }

    final ByteBuffer byteBuffer;
    final LogsViewHeader logsViewHeader;
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

    @Nonnull
    public LogsViewHeader getLogsViewHeader() {
        return logsViewHeader;
    }
}
