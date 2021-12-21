/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import io.jenkins.plugins.opentelemetry.opentelemetry.instrumentation.runtimemetrics.GarbageCollector;
import io.jenkins.plugins.opentelemetry.opentelemetry.instrumentation.runtimemetrics.MemoryPools;
import io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryMetricsSemanticConventions;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import jenkins.YesNoMaybe;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryMetricsSemanticConventions.*;

@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class JvmMonitoringInitializer extends OpenTelemetryPluginAbstractInitializer {

    private final static Logger LOGGER = Logger.getLogger(JvmMonitoringInitializer.class.getName());

    public JvmMonitoringInitializer() {

    }

    /**
     * TODO better dependency handling
     * Don't start just after `PLUGINS_STARTED` because it creates an initialization problem
     */
    @Initializer(after = InitMilestone.JOB_LOADED)
    public void initialize() {
        LOGGER.log(Level.INFO, "Start monitoring the JVM...");
        GarbageCollector.registerObservers();
        MemoryPools.registerObservers();

        final OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();

        Meter meter = GlobalOpenTelemetry.get().getMeterProvider().get(OperatingSystemMXBean.class.getName());

        meter
            .gaugeBuilder(OpenTelemetryMetricsSemanticConventions.SYSTEM_CPU_LOAD_AVERAGE_1M)
            .setDescription("System CPU load average 1 minute")
            .setUnit("%")
            .buildWithCallback(resultObserver -> resultObserver.record(operatingSystemMXBean.getSystemLoadAverage()));

        if (operatingSystemMXBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) operatingSystemMXBean;

            // PROCESS CPU
            meter
                .gaugeBuilder(OpenTelemetryMetricsSemanticConventions.PROCESS_CPU_LOAD)
                .setDescription("Process CPU load")
                .setUnit("%")
                .buildWithCallback(resultObserver -> resultObserver.record(osBean.getProcessCpuLoad()));
            meter
                .counterBuilder(OpenTelemetryMetricsSemanticConventions.PROCESS_CPU_TIME)
                .setDescription("Process CPU time")
                .setUnit("ns")
                .buildWithCallback(valueObserver -> valueObserver.record(osBean.getProcessCpuTime()));

            // SYSTEM CPU
            meter
                .gaugeBuilder(OpenTelemetryMetricsSemanticConventions.SYSTEM_CPU_LOAD)
                .setDescription("System CPU load")
                .setUnit("%")
                .buildWithCallback(valueObserver -> valueObserver.record(osBean.getSystemCpuLoad()));

            // SYSTEM MEMORY
            /*
            Extract from the Otel Collector Host Metrics
            system_memory_usage{state="free"} 3.06987008e+08
            system_memory_usage{state="inactive"} 4.968194048e+09
            system_memory_usage{state="used"} 1.1904688128e+10
            */
            meter
                .gaugeBuilder(OpenTelemetryMetricsSemanticConventions.SYSTEM_MEMORY_USAGE)
                .ofLongs()
                .setDescription("System memory usage")
                .setUnit("bytes")
                .buildWithCallback(valueObserver -> {
                        final long totalSize = osBean.getTotalPhysicalMemorySize();
                        final long freeSize = osBean.getFreePhysicalMemorySize();
                        valueObserver.record((totalSize - freeSize), STATE_USED);
                        valueObserver.record(freeSize, STATE_FREE);
                    }
                );
            meter
                .gaugeBuilder(OpenTelemetryMetricsSemanticConventions.SYSTEM_MEMORY_UTILIZATION)
                .setDescription("System memory utilization (0.0 to 1.0)")
                .setUnit("1")
                .buildWithCallback(valueObserver ->
                    {
                        final long totalSize = osBean.getTotalPhysicalMemorySize();
                        final long freeSize = osBean.getFreePhysicalMemorySize();
                        final long usedSize = totalSize - freeSize;
                        final BigDecimal utilization;
                        if (totalSize == 0) {
                            utilization = new BigDecimal(0);  // unexpected no physical reported, report 0% utilization
                        } else {
                            utilization = new BigDecimal(usedSize).divide(new BigDecimal(totalSize), MathContext.DECIMAL64);
                        }
                        LOGGER.log(Level.FINER, () -> "Memory utilization: " + utilization + ", used: " + usedSize + " bytes, free: " + freeSize + " bytes, total: " + totalSize + " bytes");
                        valueObserver.record(utilization.doubleValue());
                    }
                );

            // SYSTEM SWAP
            meter
                .gaugeBuilder(SYSTEM_PAGING_USAGE)
                .ofLongs()
                .setDescription("System swap usage")
                .setUnit("bytes")
                .buildWithCallback(valueObserver -> {
                        final long freeSize = osBean.getFreeSwapSpaceSize();
                        final long totalSize = osBean.getTotalSwapSpaceSize();
                        valueObserver.record((totalSize - freeSize), STATE_USED);
                        valueObserver.record(freeSize, STATE_FREE);
                    }
                );
            meter
                .gaugeBuilder(SYSTEM_PAGING_UTILIZATION)
                .setDescription("System swap utilization (0.0 to 1.0)")
                .setUnit("1")
                .buildWithCallback(valueObserver ->
                    {
                        final long freeSize = osBean.getFreeSwapSpaceSize();
                        final long totalSize = osBean.getTotalSwapSpaceSize();
                        final long usedSize = totalSize - freeSize;
                        final BigDecimal utilization;
                        if (totalSize == 0) {
                            utilization = new BigDecimal(0); // if no swap is allocated, report 0% utilization. Can happen in unit tests...
                        } else {
                            utilization = new BigDecimal(usedSize).divide(new BigDecimal(totalSize), MathContext.DECIMAL64);
                        }
                        LOGGER.log(Level.FINER, () -> "Swap utilization: " + utilization + ", used: " + usedSize + " bytes, free: " + freeSize + " bytes, total: " + totalSize + " bytes");
                        valueObserver.record(utilization.doubleValue());
                    }
                );
        }
    }
}
