/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import io.jenkins.plugins.opentelemetry.job.log.LogsQueryContext;

import java.util.Objects;

public class ElasticsearchLogsQueryContext implements LogsQueryContext {
    final String pitId;
    final int pageNo;

    public ElasticsearchLogsQueryContext(String pitId, int pageNo) {
        this.pitId = pitId;
        this.pageNo = pageNo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElasticsearchLogsQueryContext that = (ElasticsearchLogsQueryContext) o;
        return Objects.equals(pitId, that.pitId) && pageNo == that.pageNo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pitId, pageNo);
    }
}