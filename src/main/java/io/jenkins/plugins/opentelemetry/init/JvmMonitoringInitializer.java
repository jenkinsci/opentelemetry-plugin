/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import static io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryMetricsSemanticConventions.*;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import io.jenkins.plugins.opentelemetry.opentelemetry.instrumentation.runtimemetrics.GarbageCollector;
import io.jenkins.plugins.opentelemetry.opentelemetry.instrumentation.runtimemetrics.MemoryPools;
import io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryMetricsSemanticConventions;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;
import jenkins.YesNoMaybe;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.logging.Level;
import java.util.logging.Logger;

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

        Meter meter = GlobalMeterProvider.getMeter(OperatingSystemMXBean.class.getName());

        meter
            .doubleValueObserverBuilder(OpenTelemetryMetricsSemanticConventions.SYSTEM_CPU_LOAD_AVERAGE_1M)
            .setDescription("System CPU load average 1 minute")
            .setUnit("%")
            .setUpdater(valueObserver ->
                valueObserver.observe(operatingSystemMXBean.getSystemLoadAverage(), Labels.empty())
            )
            .build();

        if (operatingSystemMXBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) operatingSystemMXBean;

            // PROCESS CPU
            meter
                .doubleValueObserverBuilder(OpenTelemetryMetricsSemanticConventions.PROCESS_CPU_LOAD)
                .setDescription("Process CPU load")
                .setUnit("%")
                .setUpdater(valueObserver ->
                    valueObserver.observe(osBean.getProcessCpuLoad(), Labels.empty())
                )
                .build();
            meter
                .longSumObserverBuilder(OpenTelemetryMetricsSemanticConventions.PROCESS_CPU_TIME)
                .setDescription("Process CPU time")
                .setUnit("ns")
                .setUpdater(valueObserver ->
                    valueObserver.observe(osBean.getProcessCpuTime(), Labels.empty())
                )
                .build();

            // SYSTEM CPU
            meter
                .doubleValueObserverBuilder(OpenTelemetryMetricsSemanticConventions.SYSTEM_CPU_LOAD)
                .setDescription("System CPU load")
                .setUnit("%")
                .setUpdater(valueObserver ->
                    valueObserver.observe(osBean.getSystemCpuLoad(), Labels.empty())
                )
                .build();

            // SYSTEM MEMORY
            /*
            Extract from the Otel Collector Host Metrics
            system_memory_usage{state="free"} 3.06987008e+08
            system_memory_usage{state="inactive"} 4.968194048e+09
            system_memory_usage{state="used"} 1.1904688128e+10
            */
            meter
                .longUpDownSumObserverBuilder(OpenTelemetryMetricsSemanticConventions.SYSTEM_MEMORY_USAGE)
                .setDescription("System memory usage")
                .setUnit("bytes")
                .setUpdater(valueObserver -> {
                        final long totalSize = osBean.getTotalPhysicalMemorySize();
                        final long freeSize = osBean.getFreePhysicalMemorySize();
                        valueObserver.observe((totalSize - freeSize), STATE_USED);
                        valueObserver.observe(freeSize, STATE_FREE);
                    }
                )
                .build();
            meter
                .doubleValueObserverBuilder(OpenTelemetryMetricsSemanticConventions.SYSTEM_MEMORY_UTILIZATION)
                .setDescription("System memory utilization (0.0 to 1.0)")
                .setUnit("1")
                .setUpdater(valueObserver ->
                    {
                        final long totalSize = osBean.getTotalPhysicalMemorySize();
                        final long freeSize = osBean.getFreePhysicalMemorySize();
                        final long usedSize = totalSize - freeSize;
                        final BigDecimal utilization = new BigDecimal(usedSize).divide(new BigDecimal(totalSize), MathContext.DECIMAL64);
                        LOGGER.log(Level.FINER, () -> "Memory utilization: " + utilization + ", used: " + usedSize + " bytes, free: " + freeSize + " bytes, total: " + totalSize + " bytes");
                        valueObserver.observe(utilization.doubleValue(), Labels.empty());
                    }
                )
                .build();

            // SYSTEM SWAP
            meter
                .longUpDownSumObserverBuilder(SYSTEM_PAGING_USAGE)
                .setDescription("System swap usage")
                .setUnit("bytes")
                .setUpdater(valueObserver -> {
                        final long freeSize = osBean.getFreeSwapSpaceSize();
                        final long totalSize = osBean.getTotalSwapSpaceSize();
                        valueObserver.observe((totalSize - freeSize), STATE_USED);
                        valueObserver.observe(freeSize, STATE_FREE);
                    }
                )
                .build();
            meter
                .doubleValueObserverBuilder(SYSTEM_PAGING_UTILIZATION)
                .setDescription("System swap utilization (0.0 to 1.0)")
                .setUnit("bytes")
                .setUpdater(valueObserver ->
                    {
                        final long totalSize = osBean.getTotalSwapSpaceSize();
                        final long freeSize = osBean.getFreeSwapSpaceSize();
                        final long usedSize = totalSize - freeSize;
                        final BigDecimal utilization = new BigDecimal(usedSize).divide(new BigDecimal(totalSize), MathContext.DECIMAL64);
                        LOGGER.log(Level.FINER, () -> "Swap utilization: " + utilization + ", used: " + usedSize + " bytes, free: " + freeSize + " bytes, total: " + totalSize + " bytes");
                        valueObserver.observe(utilization.doubleValue(), Labels.empty());
                    }
                )
                .build();
        }
    }
}
