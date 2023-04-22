/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import org.kohsuke.stapler.framework.io.ByteBuffer;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.Charset;

public class LogsQueryResult {
    public LogsQueryResult(@NonNull ByteBuffer byteBuffer, @NonNull LogsViewHeader logsViewHeader, @NonNull Charset charset, boolean completed) {
        this.byteBuffer = byteBuffer;
        this.logsViewHeader = logsViewHeader;
        this.charset = charset;
        this.complete = completed;
    }

    final ByteBuffer byteBuffer;
    final LogsViewHeader logsViewHeader;
    final Charset charset;
    final boolean complete;

    @NonNull
    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    @NonNull
    public Charset getCharset() {
        return charset;
    }

    @NonNull
    public boolean isComplete() {
        return complete;
    }

    @NonNull
    public LogsViewHeader getLogsViewHeader() {
        return logsViewHeader;
    }
}
