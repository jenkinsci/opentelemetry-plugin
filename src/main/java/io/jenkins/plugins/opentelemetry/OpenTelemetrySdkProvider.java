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
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


@Extension
public class OpenTelemetrySdkProvider {

    private static final Logger LOGGER = Logger.getLogger(OpenTelemetrySdkProvider.class.getName());

    protected transient OpenTelemetry openTelemetry;
    protected transient OpenTelemetrySdk openTelemetrySdk;
    protected transient TracerDelegate tracer;

    protected transient SdkMeterProvider sdkMeterProvider;
    protected transient MeterProvider meterProvider;
    protected transient Meter meter;

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

    @VisibleForTesting
    @Nonnull
    protected SdkMeterProvider getSdkMeterProvider() {
        return Preconditions.checkNotNull(sdkMeterProvider);
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
        }
        if (this.sdkMeterProvider != null) {
            this.sdkMeterProvider.shutdown();
        }
        GlobalOpenTelemetry.resetForTest();
        GlobalMeterProvider.set(MeterProvider.noop());
    }

    public void initialize(@Nonnull OpenTelemetryConfiguration configuration) {
        preDestroy(); // shutdown existing SDK

        // PROPERTIES
        final Map<String, String> properties = new HashMap<>();
        if (OpenTelemetryConfiguration.TESTING_INMEMORY_MODE) {

            properties.put("otel.traces.exporter", "testing");
            properties.put("otel.metrics.exporter", "testing");
            properties.put("otel.imr.export.interval", "10ms");
        } else if (StringUtils.isNotBlank(configuration.getEndpoint())) {
            Preconditions.checkArgument(
                configuration.getEndpoint().startsWith("http://") ||
                    configuration.getEndpoint().startsWith("https://"),
                "endpoint must be prefixed by 'http://' or 'https://': %s", configuration.getEndpoint());

            properties.put("otel.traces.exporter", "otlp");
            properties.put("otel.metrics.exporter", "otlp");
            properties.put("otel.exporter.otlp.endpoint", configuration.getEndpoint());
        } else if (StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_TRACES_EXPORTER")) &&
            StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_EXPORTER_OTLP_ENDPOINT")) &&
            StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"))) {
            // Change default of "otel.traces.exporter" from "otlp" to "none" unless "otel.exporter.otlp.endpoint" or "otel.exporter.otlp.traces.endpoint" is defined
            properties.put("otel.traces.exporter", "none");
        }

        if (StringUtils.isNotBlank(configuration.getTrustedCertificatesPem())) {
            properties.put("otel.exporter.otlp.certificate", configuration.getTrustedCertificatesPem());
        }
        if (configuration.getAuthentication() != null) {
            configuration.getAuthentication().enrichOpenTelemetryAutoConfigureConfigProperties(properties);
        }
        if (configuration.getExporterTimeoutMillis() != null) {
            properties.put("otel.exporter.otlp.timeout", Integer.toString(configuration.getExporterTimeoutMillis()));
        }
        if (configuration.getExporterIntervalMillis() != null) {
            properties.put("otel.imr.export.interval", Integer.toString(configuration.getExporterIntervalMillis()));
        }

        AutoConfiguredOpenTelemetrySdkBuilder sdkBuilder = AutoConfiguredOpenTelemetrySdk.builder();
        sdkBuilder.addResourceCustomizer((resource, configProperties) -> {
            ResourceBuilder resourceBuilder = Resource.builder();
            resourceBuilder.putAll(resource);
            resourceBuilder.put(ResourceAttributes.SERVICE_NAME, JenkinsOtelSemanticAttributes.JENKINS);
            if (StringUtils.isNotBlank(configuration.getServiceName())) {
                resourceBuilder.put(ResourceAttributes.SERVICE_NAME, configuration.getServiceName());
            }
            if (StringUtils.isNotBlank(configuration.getServiceNamespace())) {
                resourceBuilder.put(ResourceAttributes.SERVICE_NAMESPACE, configuration.getServiceNamespace());
            }
            final Resource jenkinsResource = resourceBuilder.build();
            return jenkinsResource;
        });

        sdkBuilder.addPropertiesSupplier(() -> properties);

        LOGGER.log(Level.INFO, () -> "Configure SDK with properties: " + properties);
        final AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk = sdkBuilder.build();
        this.openTelemetrySdk = autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk();
        this.openTelemetry = this.openTelemetrySdk;
        this.tracer.setDelegate(openTelemetry.getTracer("jenkins"));

        this.sdkMeterProvider = (SdkMeterProvider) GlobalMeterProvider.get();
        this.meterProvider = GlobalMeterProvider.get();
        this.meter = meterProvider.get("jenkins");

        LOGGER.log(Level.INFO, () -> "OpenTelemetry initialized: " + ConfigPropertiesUtils.prettyPrintConfiguration(autoConfiguredOpenTelemetrySdk.getConfig()));
    }

    public void initializeNoOp() {
        LOGGER.log(Level.FINE, "initializeNoOp");
        preDestroy();

        this.openTelemetrySdk = null;
        this.openTelemetry = OpenTelemetry.noop();
        GlobalOpenTelemetry.set(OpenTelemetry.noop());
        if (this.tracer == null) {
            this.tracer = new TracerDelegate(OpenTelemetry.noop().getTracer("noop"));
        } else {
            this.tracer.setDelegate(OpenTelemetry.noop().getTracer("noop"));
        }

        this.sdkMeterProvider = null;
        this.meterProvider = MeterProvider.noop();
        GlobalMeterProvider.set(MeterProvider.noop());
        this.meter = meterProvider.get("jenkins");
        LOGGER.log(Level.FINE, "OpenTelemetry initialized as NoOp");
    }

    private JenkinsLocationConfiguration jenkinsLocationConfiguration;

    @Inject
    public void setJenkinsLocationConfiguration(@Nonnull JenkinsLocationConfiguration jenkinsLocationConfiguration) {
        this.jenkinsLocationConfiguration = jenkinsLocationConfiguration;
    }
}
