/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.AbstractOtelComponent;
import io.jenkins.plugins.opentelemetry.opentelemetry.instrumentation.runtimemetrics.GarbageCollector;
import io.jenkins.plugins.opentelemetry.opentelemetry.instrumentation.runtimemetrics.MemoryPools;
import io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryMetricsSemanticConventions;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jenkins.YesNoMaybe;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryMetricsSemanticConventions.*;

@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class JvmMonitoringInitializer extends AbstractOtelComponent {

    private final static Logger LOGGER = Logger.getLogger(JvmMonitoringInitializer.class.getName());

    public JvmMonitoringInitializer() {

    }

    @Override
    public void afterSdkInitialized(Meter meter, io.opentelemetry.api.logs.Logger otelLogger, Tracer tracer, ConfigProperties configProperties) {

        List<ObservableLongCounter> observableLongCounters = GarbageCollector.registerObservers(meter);
        observableLongCounters.stream().forEach(this::registerInstrument);
         List<ObservableLongUpDownCounter> observableLongUpDownCounters = MemoryPools.registerObservers(meter);
        observableLongUpDownCounters.stream().forEach(this::registerInstrument);

        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();

        registerInstrument(
            meter
                .gaugeBuilder(OpenTelemetryMetricsSemanticConventions.SYSTEM_CPU_LOAD_AVERAGE_1M)
                .setDescription("System CPU load average 1 minute")
                .setUnit("%")
                .buildWithCallback(resultObserver -> resultObserver.record(operatingSystemMXBean.getSystemLoadAverage())));

        if (operatingSystemMXBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) operatingSystemMXBean;

            // PROCESS CPU
            registerInstrument(
                meter
                    .gaugeBuilder(OpenTelemetryMetricsSemanticConventions.PROCESS_CPU_LOAD)
                    .setDescription("Process CPU load")
                    .setUnit("%")
                    .buildWithCallback(resultObserver -> resultObserver.record(osBean.getProcessCpuLoad())));
            registerInstrument(
                meter
                    .counterBuilder(OpenTelemetryMetricsSemanticConventions.PROCESS_CPU_TIME)
                    .setDescription("Process CPU time")
                    .setUnit("ns")
                    .buildWithCallback(valueObserver -> valueObserver.record(osBean.getProcessCpuTime())));

            // SYSTEM CPU
            registerInstrument(
                meter
                    .gaugeBuilder(OpenTelemetryMetricsSemanticConventions.SYSTEM_CPU_LOAD)
                    .setDescription("System CPU load")
                    .setUnit("%")
                    .buildWithCallback(valueObserver -> valueObserver.record(osBean.getSystemCpuLoad())));

            // SYSTEM MEMORY
            /*
            Extract from the Otel Collector Host Metrics
            system_memory_usage{state="free"} 3.06987008e+08
            system_memory_usage{state="inactive"} 4.968194048e+09
            system_memory_usage{state="used"} 1.1904688128e+10
            */
            registerInstrument(
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
                    ));
            registerInstrument(
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
                    ));

            // SYSTEM SWAP
            registerInstrument(
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
                    ));
            registerInstrument(
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
                    ));
        }
        LOGGER.log(Level.FINE, "Start monitoring Jenkins JVM...");
    }
}
