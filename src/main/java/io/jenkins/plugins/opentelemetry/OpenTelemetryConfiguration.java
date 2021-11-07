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
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
        this(null, null, null, 0, 0, null, null, null);
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

    public Map<String, String> toOpenTelemetryAutoConfigurationProperties() {
        Map<String, String> configuration = new HashMap<>();
        Map<String, String> resourceAttributes = new HashMap<>();

        if (StringUtils.isNotBlank(endpoint)){
            Preconditions.checkArgument(
                endpoint.startsWith("http://") ||
                    endpoint.startsWith("https://"),
                "endpoint must be prefixed by 'http://' or 'https://': %s", endpoint);

            configuration.put("otel.traces.exporter", "otlp");
            configuration.put("otel.metrics.exporter", "otlp");
            configuration.put("otel.exporter.otlp.endpoint", endpoint);
        }
        if (StringUtils.isNotBlank(serviceName)) {
            configuration.put("otel.service.name", serviceName);
        }
        if (StringUtils.isNotBlank(serviceNamespace)) {
            resourceAttributes.put(ResourceAttributes.SERVICE_NAMESPACE.getKey(), serviceNamespace);
        }
        if (StringUtils.isNotBlank(trustedCertificatesPem)) {
            configuration.put("otel.exporter.otlp.certificate", trustedCertificatesPem);
        }
        if (authentication != null) {
            authentication.enrichOpenTelemetryAutoConfigureConfigProperties(configuration);
        }
        if (exporterTimeoutMillis != null) {
            configuration.put("otel.exporter.otlp.timeout", Integer.toString(exporterTimeoutMillis));
        }
        if (exporterIntervalMillis != null) {
            configuration.put("otel.imr.export.interval", Integer.toString(exporterIntervalMillis));
        }

        configuration.put("otel.resource.attributes", OtelUtils.getComaSeparatedString(resourceAttributes));

        if (OpenTelemetryConfiguration.TESTING_INMEMORY_MODE) {
            configuration.put("otel.traces.exporter", "testing");
            configuration.put("otel.metrics.exporter", "testing");
            configuration.put("otel.imr.export.interval", "10");
        }

        return  configuration;
    }
    @Nullable
    public String getIgnoredSteps() {
        return ignoredSteps;
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
