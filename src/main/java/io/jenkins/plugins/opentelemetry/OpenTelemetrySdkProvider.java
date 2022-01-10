/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure.ConfigPropertiesUtils;
import io.jenkins.plugins.opentelemetry.opentelemetry.trace.TracerDelegate;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.extension.resources.ProcessResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


@Extension
public class OpenTelemetrySdkProvider {

    private static final Logger LOGGER = Logger.getLogger(OpenTelemetrySdkProvider.class.getName());

    /**
     * See {@code OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS}
     */
    public static final String DEFAULT_OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS = ProcessResourceProvider.class.getName();

    protected transient OpenTelemetry openTelemetry;
    protected transient OpenTelemetrySdk openTelemetrySdk;
    protected transient TracerDelegate tracer;
    protected transient Meter meter;
    protected transient Resource resource;
    protected transient ConfigProperties config;

    public OpenTelemetrySdkProvider() {
    }

    @PostConstruct
    @VisibleForTesting
    public void postConstruct() {
        initializeNoOp();
    }

    @Nonnull
    public Tracer getTracer() {
        return Preconditions.checkNotNull(tracer);
    }

    @Nonnull
    public Meter getMeter() {
        return Preconditions.checkNotNull(meter);
    }

    @Nonnull
    public Resource getResource() {
        return Preconditions.checkNotNull(resource);
    }

    @Nonnull
    public ConfigProperties getConfig() {
        return Preconditions.checkNotNull(config);
    }

    @VisibleForTesting
    @Nonnull
    protected OpenTelemetrySdk getOpenTelemetrySdk() {
        return Preconditions.checkNotNull(openTelemetrySdk);
    }

    @PreDestroy
    public void preDestroy() {
        if (this.openTelemetrySdk != null) {
            this.openTelemetrySdk.getSdkTracerProvider().shutdown();
            this.openTelemetrySdk.getSdkMeterProvider().shutdown();
        }
        GlobalOpenTelemetry.resetForTest();
    }

    public void initialize(@Nonnull OpenTelemetryConfiguration configuration) {
        preDestroy(); // shutdown existing SDK

        AutoConfiguredOpenTelemetrySdkBuilder sdkBuilder = AutoConfiguredOpenTelemetrySdk.builder();
        // PROPERTIES
        sdkBuilder.addPropertiesSupplier(() -> configuration.toOpenTelemetryProperties());

        // RESOURCE
        sdkBuilder.addResourceCustomizer((resource, configProperties) -> {
                ResourceBuilder resourceBuilder = Resource.builder()
                    .putAll(resource)
                    .putAll(configuration.toOpenTelemetryResource());
                return resourceBuilder.build();
            }
        );

        sdkBuilder.setResultAsGlobal(true); // ensure GlobalOpenTelemetry.set() is invoked
        AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk = sdkBuilder.build();
        this.openTelemetrySdk = autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk();
        this.resource = autoConfiguredOpenTelemetrySdk.getResource();
        this.config = autoConfiguredOpenTelemetrySdk.getConfig();
        LOGGER.log(Level.FINE, () -> "OpenTelemetry resource: " +
            resource.getAttributes().asMap().entrySet().stream()
                .map(e -> e.getKey().getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));
        this.openTelemetry = this.openTelemetrySdk;
        this.tracer.setDelegate(openTelemetry.getTracer("jenkins-opentelemetry", OtelUtils.getOpentelemetryPluginVersion()));

        this.meter = openTelemetry.getMeterProvider().get("jenkins-opentelemetry");

        LOGGER.log(Level.INFO, () -> "OpenTelemetry initialized: " + ConfigPropertiesUtils.prettyPrintConfiguration(autoConfiguredOpenTelemetrySdk.getConfig()));
    }

    public void initializeNoOp() {
        LOGGER.log(Level.FINE, "initializeNoOp");
        preDestroy();

        this.openTelemetrySdk = null;
        this.resource = Resource.getDefault();
        this.config = ConfigPropertiesUtils.emptyConfig();
        this.openTelemetry = OpenTelemetry.noop();
        GlobalOpenTelemetry.resetForTest(); // hack for testing in Intellij cause by DiskUsageMonitoringInitializer
        GlobalOpenTelemetry.set(OpenTelemetry.noop());
        if (this.tracer == null) {
            this.tracer = new TracerDelegate(OpenTelemetry.noop().getTracer("noop"));
        } else {
            this.tracer.setDelegate(OpenTelemetry.noop().getTracer("noop"));
        }

        this.meter = openTelemetry.getMeterProvider().get("jenkins");
        LOGGER.log(Level.FINE, "OpenTelemetry initialized as NoOp");
    }
}
