/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import java.time.Instant;
import java.util.Optional;

public class LokiGetJenkinsBuildLogsQueryParametersBuilder {
    private String jobFullName;
    private int runNumber;
    private String traceId;
    private Optional<String> flowNodeId = Optional.empty();
    private Instant startTime;
    private Optional<Instant> endTime = Optional.empty();
    private String serviceName;
    private Optional<String> serviceNamespace = Optional.empty();

    /**
     * Sets the full job name.
     *
     * @param jobFullName full job name
     * @return this builder
     */
    public LokiGetJenkinsBuildLogsQueryParametersBuilder setJobFullName(String jobFullName) {
        this.jobFullName = jobFullName;
        return this;
    }

    /**
     * Sets the build run number.
     *
     * @param runNumber build run number
     * @return this builder
     */
    public LokiGetJenkinsBuildLogsQueryParametersBuilder setRunNumber(int runNumber) {
        this.runNumber = runNumber;
        return this;
    }

    /**
     * Sets the trace ID.
     *
     * @param traceId OpenTelemetry trace ID
     * @return this builder
     */
    public LokiGetJenkinsBuildLogsQueryParametersBuilder setTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    /**
     * Sets the pipeline flow node ID.
     *
     * @param flowNodeId pipeline flow node ID, or {@code null}
     * @return this builder
     */
    public LokiGetJenkinsBuildLogsQueryParametersBuilder setFlowNodeId(String flowNodeId) {
        this.flowNodeId = Optional.ofNullable(flowNodeId);
        return this;
    }

    /**
     * Sets the build start time.
     *
     * @param startTime build start time
     * @return this builder
     */
    public LokiGetJenkinsBuildLogsQueryParametersBuilder setStartTime(Instant startTime) {
        this.startTime = startTime;
        return this;
    }

    /**
     * Sets the build end time.
     *
     * @param endTime build end time, or {@code null} if the build has not finished
     * @return this builder
     */
    public LokiGetJenkinsBuildLogsQueryParametersBuilder setEndTime(Instant endTime) {
        this.endTime = Optional.ofNullable(endTime);
        return this;
    }

    /**
     * Sets the service name.
     *
     * @param serviceName OpenTelemetry service name
     * @return this builder
     */
    public LokiGetJenkinsBuildLogsQueryParametersBuilder setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    /**
     * Sets the service namespace.
     *
     * @param serviceNamespace OpenTelemetry service namespace, or {@code null}
     * @return this builder
     */
    public LokiGetJenkinsBuildLogsQueryParametersBuilder setServiceNamespace(String serviceNamespace) {
        this.serviceNamespace = Optional.ofNullable(serviceNamespace);
        return this;
    }

    /**
     * Sets the service namespace from an Optional.
     *
     * @param serviceNamespace OpenTelemetry service namespace
     * @return this builder
     */
    public LokiGetJenkinsBuildLogsQueryParametersBuilder setServiceNamespace(Optional<String> serviceNamespace) {
        this.serviceNamespace = serviceNamespace;
        return this;
    }

    /**
     * Builds the query parameters.
     *
     * @return configured query parameters
     */
    public LokiGetJenkinsBuildLogsQueryParameters build() {
        return new LokiGetJenkinsBuildLogsQueryParameters(
                jobFullName, runNumber, traceId, flowNodeId, startTime, endTime, serviceName, serviceNamespace);
    }
}
