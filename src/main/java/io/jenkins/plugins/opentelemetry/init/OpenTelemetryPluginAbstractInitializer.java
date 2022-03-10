/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

public abstract class OpenTelemetryPluginAbstractInitializer {

    @SuppressFBWarnings(value = "URF_UNREAD_FIELD", justification = "will be used once `GlobalMeterProvider#getMeter(String)` is replaced by a getter on `OpenTelemetrySdk`")
    OpenTelemetrySdkProvider openTelemetrySdkProvider;

    /**
     * WARNING do not remove this setter used to surface the dependency to first initialize the OpenTelemetry SDK and then register metrics.
     * Note that once {@link io.jenkins.plugins.opentelemetry.opentelemetry.instrumentation.runtimemetrics.MemoryPools} and
     * {@link io.jenkins.plugins.opentelemetry.opentelemetry.instrumentation.runtimemetrics.GarbageCollector} stop
     * using {@link io.opentelemetry.api.GlobalOpenTelemetry}, proper dependency injection can be implemented.
     */
    @Inject
    public void setOpenTelemetrySdkProvider(@NonNull OpenTelemetrySdkProvider openTelemetrySdkProvider){
        this.openTelemetrySdkProvider = openTelemetrySdkProvider;
    }
}
