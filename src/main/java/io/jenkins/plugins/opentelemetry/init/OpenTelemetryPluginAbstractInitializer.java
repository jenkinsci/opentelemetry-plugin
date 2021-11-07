/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.opentelemetry.api.metrics.GlobalMeterProvider;

import javax.annotation.Nonnull;
import javax.inject.Inject;

public abstract class OpenTelemetryPluginAbstractInitializer {

    @SuppressFBWarnings(value = "URF_UNREAD_FIELD", justification = "will be used once `GlobalMeterProvider#getMeter(String)` is replaced by a getter on `OpenTelemetrySdk`")
    private OpenTelemetrySdkProvider openTelemetrySdkProvider;

    /**
     * WARNING do not remove this setter used to surface the dependency to first initialize the OpenTelemetry SDK and then register metrics.
     * Note that once {@link GlobalMeterProvider#get()}} is replaced by a getter on {@link io.opentelemetry.api.OpenTelemetry},
     * then the problem dependency will become explicit.
     */
    @Inject
    public void setOpenTelemetrySdkProvider(@Nonnull OpenTelemetrySdkProvider openTelemetrySdkProvider){
        this.openTelemetrySdkProvider = openTelemetrySdkProvider;
    }
}
