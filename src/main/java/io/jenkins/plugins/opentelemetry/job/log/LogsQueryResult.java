/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import java.nio.charset.Charset;

public class LogsQueryResult {
    public LogsQueryResult(@Nonnull ByteBuffer byteBuffer, @Nonnull LogsViewHeader logsViewHeader, @Nonnull Charset charset, boolean completed) {
        this.byteBuffer = byteBuffer;
        this.logsViewHeader = logsViewHeader;
        this.charset = charset;
        this.complete = completed;
    }

    final ByteBuffer byteBuffer;
    final LogsViewHeader logsViewHeader;
    final Charset charset;
    final boolean complete;

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
    public LogsViewHeader getLogsViewHeader() {
        return logsViewHeader;
    }
}
