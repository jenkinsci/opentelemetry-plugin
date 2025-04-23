/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import static io.jenkins.plugins.opentelemetry.backend.grafana.LokiMetadata.LABEL_SERVICE_NAME;
import static io.jenkins.plugins.opentelemetry.backend.grafana.LokiMetadata.LABEL_SERVICE_NAMESPACE;
import static io.jenkins.plugins.opentelemetry.backend.grafana.LokiMetadata.META_DATA_CI_PIPELINE_ID;
import static io.jenkins.plugins.opentelemetry.backend.grafana.LokiMetadata.META_DATA_CI_PIPELINE_RUN_NUMBER;
import static io.jenkins.plugins.opentelemetry.backend.grafana.LokiMetadata.META_DATA_JENKINS_PIPELINE_STEP_ID;
import static io.jenkins.plugins.opentelemetry.backend.grafana.LokiMetadata.META_DATA_TRACE_ID;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.Nonnull;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

public class LokiGetJenkinsBuildLogsQueryParameters {
    @NonNull
    private final String jobFullName;
    private final int runNumber;
    @NonNull
    private final String traceId;
    @NonNull
    private final Optional<String> flowNodeId;
    @NonNull
    private Long startTimeInNanos;
    @NonNull
    private final Optional<Long> endTimeInNanos;
    @NonNull
    private final String serviceName;
    @NonNull
    private final Optional<String> serviceNamespace;

    public LokiGetJenkinsBuildLogsQueryParameters(@NonNull String jobFullName, int runNumber, @NonNull String traceId, @NonNull Optional<String> flowNodeId, @NonNull Instant startTimeInNanos, @NonNull Optional<Instant> endTime, @NonNull String serviceName, @NonNull Optional<String> serviceNamespace) {
        this.jobFullName = jobFullName;
        this.runNumber = runNumber;
        this.traceId = traceId;
        this.flowNodeId = flowNodeId;
        this.startTimeInNanos = instantToEpochNanos(startTimeInNanos);
        this.endTimeInNanos = endTime.map(new InstantToEpochInNanos());
        this.serviceName = serviceName;
        this.serviceNamespace = serviceNamespace;
    }

    public ClassicHttpRequest toHttpRequest(@Nonnull String lokiUrl) {
        // https://grafana.com/docs/loki/latest/reference/loki-http-api/#query-logs-within-a-range-of-time

        final StringBuilder logQl = new StringBuilder("{");
        serviceNamespace.ifPresent(serviceNamespace -> logQl.append(LABEL_SERVICE_NAMESPACE).append("=\"").append(serviceNamespace).append("\", "));
        logQl.append(LABEL_SERVICE_NAME + "=\"" + serviceName + "\"}");

        logQl.append("|" +
            META_DATA_TRACE_ID + "=\"" + traceId + "\", " +
            META_DATA_CI_PIPELINE_ID + "=\"" + jobFullName + "\", " +
            META_DATA_CI_PIPELINE_RUN_NUMBER + "=" + runNumber);
        flowNodeId.ifPresent(flowNodeId -> logQl.append(", " + META_DATA_JENKINS_PIPELINE_STEP_ID + "=\"" + flowNodeId + "\""));

        logQl.append(" | keep __line__");

        ClassicRequestBuilder lokiQueryRangeRequestBuilder = ClassicRequestBuilder
            .get()
            .setUri(lokiUrl + "/loki/api/v1/query_range")
            .addParameter("query", logQl.toString())
            .addParameter("start",  startTimeInNanos + "")
            .addParameter("direction", "forward");

        endTimeInNanos
            .ifPresent(endTimeInNanos -> lokiQueryRangeRequestBuilder.addParameter("end", String.valueOf(endTimeInNanos)));

        return lokiQueryRangeRequestBuilder.build();
    }

    public Attributes toAttributes() {
        final AttributesBuilder attributesBuilder = Attributes.builder();
        attributesBuilder.put("query." + META_DATA_TRACE_ID, traceId);
        attributesBuilder.put("query." + META_DATA_CI_PIPELINE_ID, jobFullName);
        attributesBuilder.put("query." + META_DATA_CI_PIPELINE_RUN_NUMBER, runNumber);
        flowNodeId.ifPresent(flowNodeId -> attributesBuilder.put("query." +META_DATA_JENKINS_PIPELINE_STEP_ID, flowNodeId));

        attributesBuilder.put("query.startTimeInNanos", startTimeInNanos);
        endTimeInNanos.ifPresent(endTimeInNanos -> attributesBuilder.put("query.endTimeInNanos", endTimeInNanos));

        return attributesBuilder.build();
    }

    public void setStartTimeInNanos(long startTimeInNanos) {
        this.startTimeInNanos = startTimeInNanos;
    }

    @NonNull
    public Long getStartTimeInNanos() {
        return startTimeInNanos;
    }

    @Override
    public String toString() {
        return "LokiGetJenkinsBuildLogsQueryParameters{" +
            "jobFullName='" + jobFullName + '\'' +
            ", runNumber=" + runNumber +
            ", traceId='" + traceId + '\'' +
            ", flowNodeId=" + flowNodeId +
            ", startTimeInNanos=" + startTimeInNanos +
            ", endTimeInNanos=" + endTimeInNanos +
            ", serviceName='" + serviceName + '\'' +
            ", serviceNamespace=" + serviceNamespace +
            '}';
    }

    static long instantToEpochNanos(Instant instant) {
        return new InstantToEpochInNanos().apply(instant);
    }

    static class InstantToEpochInNanos implements Function<Instant, Long> {
        @Override
        @Nonnull
        public Long apply(Instant instant) {
            return TimeUnit.NANOSECONDS.convert(instant.toEpochMilli(), TimeUnit.MILLISECONDS) + instant.getNano();
        }
    }
}
