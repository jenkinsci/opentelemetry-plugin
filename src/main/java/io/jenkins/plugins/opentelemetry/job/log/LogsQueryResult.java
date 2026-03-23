/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.Charset;
import org.kohsuke.stapler.framework.io.ByteBuffer;

/** Value object containing log bytes and metadata returned by a log backend query. */
public class LogsQueryResult {
    /**
     * Creates a new logs query result.
     *
     * @param byteBuffer log payload buffer
     * @param logsViewHeader metadata displayed in the Jenkins log header
     * @param charset character set used to decode the buffer
     * @param completed whether the source log stream is complete
     */
    public LogsQueryResult(
            @NonNull ByteBuffer byteBuffer,
            @NonNull LogsViewHeader logsViewHeader,
            @NonNull Charset charset,
            boolean completed) {
        this.byteBuffer = byteBuffer;
        this.logsViewHeader = logsViewHeader;
        this.charset = charset;
        this.complete = completed;
    }

    final ByteBuffer byteBuffer;
    final LogsViewHeader logsViewHeader;
    final Charset charset;
    final boolean complete;

    /**
     * Returns the log payload buffer.
     *
     * @return log payload buffer
     */
    @NonNull
    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    /**
     * Returns the character set associated with the log payload.
     *
     * @return payload character set
     */
    @NonNull
    public Charset getCharset() {
        return charset;
    }

    /**
     * Indicates whether all log lines have already been produced.
     *
     * @return {@code true} when no additional log lines are expected
     */
    @NonNull
    public boolean isComplete() {
        return complete;
    }

    /**
     * Returns metadata used to render the logs backend header in Jenkins.
     *
     * @return logs header metadata
     */
    @NonNull
    public LogsViewHeader getLogsViewHeader() {
        return logsViewHeader;
    }
}
