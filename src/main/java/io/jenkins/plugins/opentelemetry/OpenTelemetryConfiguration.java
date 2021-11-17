/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.plugins.opentelemetry.authentication.NoAuthentication;
import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

public class OpenTelemetryConfiguration {

    @SuppressFBWarnings
    @VisibleForTesting
    public static boolean TESTING_INMEMORY_MODE = false;

    private final String endpoint;
    private final String trustedCertificatesPem;
    private final OtlpAuthentication authentication;
    private final Integer exporterTimeoutMillis;
    private final Integer exporterIntervalMillis;
    private final String ignoredSteps;
    private final String serviceName;
    private final String serviceNamespace;

    public OpenTelemetryConfiguration() {
        this(null, null, null, null, null, null, null, null);
    }

    public OpenTelemetryConfiguration(@Nullable String endpoint, @Nullable String trustedCertificatesPem, @Nullable OtlpAuthentication authentication,
                                      @Nullable Integer exporterTimeoutMillis, @Nullable Integer exporterIntervalMillis, @Nullable String ignoredSteps,
                                      @Nullable String serviceName, @Nullable String serviceNamespace) {
        this.endpoint = endpoint;
        this.trustedCertificatesPem = trustedCertificatesPem;
        this.authentication = authentication;
        this.exporterTimeoutMillis = exporterTimeoutMillis;
        this.exporterIntervalMillis = exporterIntervalMillis;
        this.ignoredSteps = ignoredSteps;
        this.serviceName = serviceName;
        this.serviceNamespace = serviceNamespace;
    }

    @Nullable
    public String getEndpoint() {
        return endpoint;
    }

    @Nonnull
    public String getServiceName() {
        return serviceName;
    }

    @Nullable
    public String getServiceNamespace() {
        return serviceNamespace;
    }

    /**
     * @return default to {@link NoAuthentication}
     */
    @Nonnull
    public OtlpAuthentication getAuthentication() {
        return authentication == null ? new NoAuthentication() : authentication;
    }

    @Nullable
    public String getTrustedCertificatesPem() {
        return trustedCertificatesPem;
    }

    @Nullable
    public Integer getExporterTimeoutMillis() {
        return exporterTimeoutMillis;
    }

    @Nullable
    public Integer getExporterIntervalMillis() {
        return exporterIntervalMillis;
    }

    @Nullable
    public String getIgnoredSteps() {
        return ignoredSteps;
    }

    /**
     * @see io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder#addPropertiesSupplier(java.util.function.Supplier)
     */
    @Nonnull
    public Map<String, String> toOpenTelemetryProperties() {
        Map<String, String> properties = new HashMap<>();
        if (TESTING_INMEMORY_MODE) {
            properties.put("otel.traces.exporter", "testing");
            properties.put("otel.metrics.exporter", "testing");
            properties.put("otel.imr.export.interval", "10ms");
        } else if (StringUtils.isNotBlank(this.getEndpoint())) {
            Preconditions.checkArgument(
                this.getEndpoint().startsWith("http://") ||
                    this.getEndpoint().startsWith("https://"),
                "endpoint must be prefixed by 'http://' or 'https://': %s", this.getEndpoint());

            properties.put("otel.traces.exporter", "otlp");
            properties.put("otel.metrics.exporter", "otlp");
            properties.put("otel.exporter.otlp.endpoint", this.getEndpoint());
        } else if (StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_TRACES_EXPORTER")) &&
            StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_EXPORTER_OTLP_ENDPOINT")) &&
            StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"))) {
            // Change default of "otel.traces.exporter" from "otlp" to "none" unless "otel.exporter.otlp.endpoint" or "otel.exporter.otlp.traces.endpoint" is defined
            properties.put("otel.traces.exporter", "none");
        }

        if (StringUtils.isNotBlank(this.getTrustedCertificatesPem())) {
            properties.put("otel.exporter.otlp.certificate", this.getTrustedCertificatesPem());
        }
        if (this.getAuthentication() != null) {
            this.getAuthentication().enrichOpenTelemetryAutoConfigureConfigProperties(properties);
        }
        if (this.getExporterTimeoutMillis() != null) {
            properties.put("otel.exporter.otlp.timeout", Integer.toString(this.getExporterTimeoutMillis()));
        }
        if (this.getExporterIntervalMillis() != null) {
            properties.put("otel.imr.export.interval", Integer.toString(this.getExporterIntervalMillis()));
        }
        return properties;
    }

    /**
     *
     * @see io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder#addResourceCustomizer(BiFunction)
     */
    @Nonnull
    public Resource toOpenTelemetryResource() {
        ResourceBuilder resourceBuilder = Resource.builder();
        if (StringUtils.isNotBlank(this.getServiceName())) {
            resourceBuilder.put(ResourceAttributes.SERVICE_NAME, this.getServiceName());
        }
        if (StringUtils.isNotBlank(this.getServiceNamespace())) {
            resourceBuilder.put(ResourceAttributes.SERVICE_NAMESPACE, this.getServiceNamespace());
        }
        return resourceBuilder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenTelemetryConfiguration that = (OpenTelemetryConfiguration) o;
        return Objects.equals(endpoint, that.endpoint) && Objects.equals(authentication, that.authentication) &&
                Objects.equals(trustedCertificatesPem, that.trustedCertificatesPem) && Objects.equals(exporterTimeoutMillis, that.exporterTimeoutMillis) &&
                Objects.equals(exporterIntervalMillis, that.exporterIntervalMillis) && Objects.equals(ignoredSteps, that.ignoredSteps) &&
                Objects.equals(serviceName, that.serviceName) && Objects.equals(serviceNamespace, that.serviceNamespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoint, authentication, trustedCertificatesPem, exporterTimeoutMillis, exporterIntervalMillis, serviceName, serviceNamespace);
    }

    @Override
    public String toString() {
        return "OpenTelemetryConfiguration{" +
                "endpoint='" + endpoint + '\'' +
                ", trustedCertificatesPem=" + ((!(Strings.isNullOrEmpty(trustedCertificatesPem)))) +
                ", authentication=" + authentication +
                ", exporterTimeoutMillis=" + exporterTimeoutMillis +
                ", exporterIntervalMillis=" + exporterIntervalMillis +
                ", ignoredSteps=" + ignoredSteps +
                ", serviceName=" + serviceName +
                ", serviceNamespace=" + serviceNamespace +
                '}';
    }
}
