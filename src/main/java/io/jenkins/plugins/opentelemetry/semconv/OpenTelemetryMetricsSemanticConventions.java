/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.semconv;

import io.opentelemetry.api.metrics.common.Labels;

/**
 * Java constants for the
 * [OpenTelemetry System Metrics Semantic Conventions](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/system-metrics.md)
 * [OpenTelemetry Process Metrics Semantic Conventions](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/process-metrics.md)
 */
public class OpenTelemetryMetricsSemanticConventions {

    public final static String SYSTEM_CPU_LOAD = "system.cpu.load";
    public final static String SYSTEM_CPU_LOAD_AVERAGE_1M = "system.cpu.load.average.1m";
    public final static String SYSTEM_MEMORY_USAGE = "system.memory.usage";
    public final static String SYSTEM_MEMORY_UTILIZATION = "system.memory.utilization";
    public final static String SYSTEM_PAGING_USAGE = "system.paging.usage";
    public final static String SYSTEM_PAGING_UTILIZATION = "system.paging.utilization";

    public final static Labels STATE_FREE = Labels.of("state", "free");
    public final static Labels STATE_USED = Labels.of("state", "used");

    public final static String PROCESS_CPU_LOAD = "process.cpu.load";
    public final static String PROCESS_CPU_TIME = "process.cpu.time";
}
