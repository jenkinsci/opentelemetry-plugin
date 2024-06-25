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

    public LokiGetJenkinsBuildLogsQueryParametersBuilder setJobFullName(String jobFullName) {
        this.jobFullName = jobFullName;
        return this;
    }

    public LokiGetJenkinsBuildLogsQueryParametersBuilder setRunNumber(int runNumber) {
        this.runNumber = runNumber;
        return this;
    }

    public LokiGetJenkinsBuildLogsQueryParametersBuilder setTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    public LokiGetJenkinsBuildLogsQueryParametersBuilder setFlowNodeId(String flowNodeId) {
        this.flowNodeId = Optional.ofNullable(flowNodeId);
        return this;
    }

    public LokiGetJenkinsBuildLogsQueryParametersBuilder setStartTime(Instant startTime) {
        this.startTime = startTime;
        return this;
    }

    public LokiGetJenkinsBuildLogsQueryParametersBuilder setEndTime(Instant endTime) {
        this.endTime = Optional.ofNullable(endTime);
        return this;
    }

    public LokiGetJenkinsBuildLogsQueryParametersBuilder setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public LokiGetJenkinsBuildLogsQueryParametersBuilder setServiceNamespace(String serviceNamespace) {
        this.serviceNamespace =  Optional.ofNullable(serviceNamespace);
        return this;
    }
    public LokiGetJenkinsBuildLogsQueryParametersBuilder setServiceNamespace(Optional<String> serviceNamespace) {
        this.serviceNamespace =  serviceNamespace;
        return this;
    }

    public LokiGetJenkinsBuildLogsQueryParameters build() {
        return new LokiGetJenkinsBuildLogsQueryParameters(jobFullName, runNumber, traceId, flowNodeId, startTime, endTime, serviceName, serviceNamespace);
    }
}