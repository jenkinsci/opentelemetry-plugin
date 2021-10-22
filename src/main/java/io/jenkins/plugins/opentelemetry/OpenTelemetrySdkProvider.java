/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure.ConfigPropertiesUtils;
import io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure.DefaultConfigProperties;
import io.jenkins.plugins.opentelemetry.opentelemetry.trace.TracerDelegate;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

        // CONFIGURATION DEFAULTS
        Map<String, String> configurationDefaults = new HashMap<>();
        configurationDefaults.put("otel.service.name", JenkinsOtelSemanticAttributes.JENKINS);
        configurationDefaults.put("otel.resource.attributes", ResourceAttributes.SERVICE_NAMESPACE.getKey() + "=" + JenkinsOtelSemanticAttributes.JENKINS);

        // OVERWRITES OF THE OTEL AUTO CONFIGURATION
        Map<String, String> configurationOverwrites = new HashMap<>();

        // Change default of "otel.traces.exporter" from "otlp" to "none" unless "otel.exporter.otlp.endpoint" or "otel.exporter.otlp.traces.endpoint" is defined
        if (StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_TRACES_EXPORTER")) &&
            StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_EXPORTER_OTLP_ENDPOINT")) &&
            StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"))) {
            configurationOverwrites.put("otel.traces.exporter", "none");
        }

        // Put configuration params specified in the Jenkins plugin configuration
        configurationOverwrites.putAll(configuration.toOpenTelemetryAutoConfigurationProperties());

        // Use JenkinsLocationConfiguration because invoking Jenkins.getInstanceOrNull() causes an infinite loop,
        String rootUrl = jenkinsLocationConfiguration == null ? "#unknown#" : Objects.toString(jenkinsLocationConfiguration.getUrl(), "#undefined#");
        configurationOverwrites.put("otel.resource.attributes", JenkinsOtelSemanticAttributes.JENKINS_URL.getKey() + "=" + rootUrl);


        ConfigProperties configProperties = DefaultConfigProperties.createFromConfiguration(configurationOverwrites, configurationDefaults);

        this.openTelemetrySdk = OpenTelemetrySdkAutoConfiguration.initialize(true, configProperties);
        this.openTelemetry = this.openTelemetrySdk;
        this.tracer.setDelegate(openTelemetry.getTracer("jenkins"));

        this.sdkMeterProvider = (SdkMeterProvider) GlobalMeterProvider.get();
        this.meterProvider = GlobalMeterProvider.get();
        this.meter = meterProvider.get("jenkins");

        LOGGER.log(Level.INFO, () -> "OpenTelemetry initialized: " + ConfigPropertiesUtils.prettyPrintConfiguration(configProperties));
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
