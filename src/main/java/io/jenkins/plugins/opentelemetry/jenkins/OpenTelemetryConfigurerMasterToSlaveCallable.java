/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.jenkins;

import io.jenkins.plugins.opentelemetry.opentelemetry.GlobalOpenTelemetrySdk;
import jenkins.security.MasterToSlaveCallable;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OpenTelemetryConfigurerMasterToSlaveCallable extends MasterToSlaveCallable<Object, RuntimeException> {
    static final Logger logger = Logger.getLogger(OpenTelemetryConfigurerMasterToSlaveCallable.class.getName());

    final Map<String, String> otelSdkConfigurationProperties;
    final Map<String, String> otelSdkResource;

    public OpenTelemetryConfigurerMasterToSlaveCallable(Map<String, String> otelSdkConfigurationProperties, Map<String, String> otelSdkResource) {
        this.otelSdkConfigurationProperties = otelSdkConfigurationProperties;
        this.otelSdkResource = otelSdkResource;
    }

    @Override
    public Object call() throws RuntimeException {
        logger.log(Level.FINER, () -> "Configure OpenTelemetry SDK with properties: " + otelSdkConfigurationProperties + ", resource:" + otelSdkResource);
        GlobalOpenTelemetrySdk.configure(otelSdkConfigurationProperties, otelSdkResource, true);
        return null;
    }
}
