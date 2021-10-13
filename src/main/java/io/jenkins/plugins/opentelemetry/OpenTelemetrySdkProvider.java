/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
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
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


@Extension
public class OpenTelemetrySdkProvider {

    private static final Logger LOGGER = Logger.getLogger(OpenTelemetrySdkProvider.class.getName());

    @SuppressFBWarnings
    @VisibleForTesting
    public static boolean TESTING_INMEMORY_MODE = false;

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
    protected SdkMeterProvider getSdkMeterProvider(){
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

        Map<String, String> configProperties = new HashMap<>();

        if (TESTING_INMEMORY_MODE) {
            LOGGER.log(Level.FINE, "Initialize for testing");

            configProperties.put("otel.traces.exporter", "testing");
            configProperties.put("otel.metrics.exporter", "testing");

        } else if (StringUtils.isNotBlank(configuration.getEndpoint())) {
            LOGGER.log(Level.FINE, "Initialize GRPC");

            Preconditions.checkArgument(
                configuration.getEndpoint().startsWith("http://") ||
                    configuration.getEndpoint().startsWith("https://"),
                "endpoint must be prefixed by 'http://' or 'https://': %s", configuration.getEndpoint());

            configProperties.put("otel.traces.exporter", "otlp");
            configProperties.put("otel.metrics.exporter", "otlp");
            configProperties.put("otel.exporter.otlp.endpoint", configuration.getEndpoint());
        } else if (StringUtils.isNotBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("otel.exporter.otlp.endpoint", "OTEL_EXPORTER_OTLP_ENDPOINT"))) {
            LOGGER.log(Level.FINE, "Initialize GRPC auto configuration");
            // use autoconfigure endpoint
        } else {
            configProperties.put("otel.traces.exporter", "none");
            configProperties.put("otel.metrics.exporter", "none");
            // TODO support cases where users don't want the OTLP exporter (ie just Prometheus Exposition Format)
        }

        // otel.resource.attributes	OTEL_RESOURCE_ATTRIBUTES
        String initialComaSeparatedAttributes = OtelUtils.getSystemPropertyOrEnvironmentVariable("otel.resource.attributes", "OTEL_RESOURCE_ATTRIBUTES");
        Map<String, String> attributes = OtelUtils.getCommaSeparatedMap(initialComaSeparatedAttributes);

        // otel.service.name	OTEL_SERVICE_NAME
        if (StringUtils.isNotBlank(configuration.getServiceName())) {
            configProperties.put("otel.service.name", configuration.getServiceName());
        } else if (StringUtils.isNotBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable(
            "otel.service.name", "OTEL_SERVICE_NAME"))) {
            // use autoconfigure service.name
        } else {
            attributes.put(ResourceAttributes.SERVICE_NAME.getKey(), JenkinsOtelSemanticAttributes.JENKINS);
        }

        // service.namespace
        if (StringUtils.isNotBlank(configuration.getServiceNamespace())) {
            attributes.put(ResourceAttributes.SERVICE_NAMESPACE.getKey(), configuration.getServiceNamespace());
        }

        // As invoking Jenkins.getInstanceOrNull() causes an infinite loop, use JenkinsLocationConfiguration
        String rootUrl = jenkinsLocationConfiguration == null ? "#unknown#" : Objects.toString(jenkinsLocationConfiguration.getUrl(), "#undefined#");
        attributes.put(JenkinsOtelSemanticAttributes.JENKINS_URL.getKey(), rootUrl);

        // otel.exporter.otlp.certificate OTEL_EXPORTER_OTLP_CERTIFICATE
        if (StringUtils.isNotBlank(configuration.getTrustedCertificatesPem())) {
            configProperties.put("otel.exporter.otlp.certificate", configuration.getTrustedCertificatesPem());
        }

        // otel.exporter.otlp.headers	OTEL_EXPORTER_OTLP_HEADERS

        // authentication
        OtlpAuthentication authentication = configuration.getAuthentication();
        authentication.enrichOpenTelemetryAutoConfigureConfigProperties(configProperties);

        // otel.exporter.otlp.timeout	OTEL_EXPORTER_OTLP_TIMEOUT
        configProperties.put("otel.exporter.otlp.timeout", Integer.toString(configuration.getExporterTimeoutMillis()));

        // otel.imr.export.interval	OTEL_IMR_EXPORT_INTERVAL Interval Metric Reader
        configProperties.put("otel.imr.export.interval", Integer.toString(configuration.getExporterTimeoutMillis()));

        String newComaSeparatedAttributes = OtelUtils.getComaSeparatedString(attributes);
        LOGGER.log(Level.FINE, () -> "Initial resource attributes:" + initialComaSeparatedAttributes);
        LOGGER.log(Level.FINE, () -> "Use resource attributes: " + newComaSeparatedAttributes);
        configProperties.put("otel.resource.attributes", newComaSeparatedAttributes);

        final Properties properties = new Properties(System.getProperties());
        properties.putAll(configProperties);
        DefaultConfigProperties defaultConfigProperties = new DefaultConfigProperties(properties, System.getenv());

        this.openTelemetrySdk = OpenTelemetrySdkAutoConfiguration.initialize(true, defaultConfigProperties);
        this.openTelemetry = this.openTelemetrySdk;
        this.tracer.setDelegate(openTelemetry.getTracer("jenkins"));

        this.sdkMeterProvider = (SdkMeterProvider) GlobalMeterProvider.get();
        this.meterProvider = GlobalMeterProvider.get();
        this.meter = meterProvider.get("jenkins");

        LOGGER.log(Level.INFO, () -> "OpenTelemetry initialized " + newComaSeparatedAttributes);
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
