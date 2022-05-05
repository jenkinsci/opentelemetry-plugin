/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.instrumentation.runtimemetrics;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongCounter;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/*
 * Copy of https://raw.githubusercontent.com/open-telemetry/opentelemetry-java-instrumentation/v1.13.1/instrumentation/runtime-metrics/library/src/main/java/io/opentelemetry/instrumentation/runtimemetrics/GarbageCollector.java
 */

/**
 * Registers observers that generate metrics about JVM garbage collectors.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * GarbageCollector.registerObservers(GlobalOpenTelemetry.get());
 * }</pre>
 *
 * <p>Example metrics being exported:
 *
 * <pre>
 *   runtime.jvm.gc.time{gc="PS1"} 6.7
 *   runtime.jvm.gc.count{gc="PS1"} 1
 * </pre>
 */
public final class GarbageCollector {
    private static final AttributeKey<String> GC_KEY = AttributeKey.stringKey("gc");

    public static List<ObservableLongCounter> registerObservers(Meter meter) {
        List<GarbageCollectorMXBean> garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans();
        List<Attributes> labelSets = new ArrayList<>(garbageCollectors.size());
        for (GarbageCollectorMXBean gc : garbageCollectors) {
            labelSets.add(Attributes.of(GC_KEY, gc.getName()));
        }
        List<ObservableLongCounter> instruments = new ArrayList<>(2);
        instruments.add(
            meter
                .counterBuilder("runtime.jvm.gc.time")
                .setDescription("Time spent in a given JVM garbage collector in milliseconds.")
                .setUnit("ms")
                .buildWithCallback(
                    resultLongObserver -> {
                        for (int i = 0; i < garbageCollectors.size(); i++) {
                            resultLongObserver.record(
                                garbageCollectors.get(i).getCollectionTime(), labelSets.get(i));
                        }
                    }));
        instruments.add(
            meter
                .counterBuilder("runtime.jvm.gc.count")
                .setDescription(
                    "The number of collections that have occurred for a given JVM garbage collector.")
                .setUnit("collections")
                .buildWithCallback(
                    resultLongObserver -> {
                        for (int i = 0; i < garbageCollectors.size(); i++) {
                            resultLongObserver.record(
                                garbageCollectors.get(i).getCollectionCount(), labelSets.get(i));
                        }
                    }));
        return instruments;
    }

    private GarbageCollector() {
    }
}