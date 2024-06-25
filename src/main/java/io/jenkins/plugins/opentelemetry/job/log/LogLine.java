/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import javax.annotation.Nonnull;

/**
 * Represents a build log line.
 *
 * @param <Id> identifier of the log line within the search
 *             query results (e.g. {@link Long} line number for Elasticsearch
 *             or the {@link Long} timestamp in nanos for Loki)
 */
public class LogLine<Id> {
    private final Id id;
    private final String message;

    public LogLine(@Nonnull Id id, @Nonnull String message) {
        this.id = id;
        this.message = message;
    }

    /**
     * @return the identifier of the log line within the search
     * query results (e.g. {@link Long} line number for Elasticsearch
     * or the {@link Long} timestamp in nanos for Loki)
     */
    @Nonnull
    public Id getId() {
        return id;
    }

    @Nonnull
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "LogLine{" +
            "id=" + id +
            ", message='" + message + '\'' +
            '}';
    }
}
