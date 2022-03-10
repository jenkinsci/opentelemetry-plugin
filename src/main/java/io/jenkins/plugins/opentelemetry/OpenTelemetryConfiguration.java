/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.apache.commons.lang.StringUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

public class OpenTelemetryConfiguration {

    @SuppressFBWarnings
    @VisibleForTesting
    public static boolean TESTING_INMEMORY_MODE = false;

    private final Optional<String> endpoint;
    private final Optional<String> trustedCertificatesPem;
    private final Optional<OtlpAuthentication> authentication;
    private final Optional<Integer> exporterTimeoutMillis;
    private final Optional<Integer> exporterIntervalMillis;
    private final Optional<String> serviceName;
    private final Optional<String> serviceNamespace;
    private final Optional<String> disabledResourceProviders;
    private final Map<String, String> configurationProperties;

    public OpenTelemetryConfiguration() {
        this(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Collections.emptyMap());
    }

    public OpenTelemetryConfiguration(Optional<String> endpoint, Optional<String> trustedCertificatesPem, Optional<OtlpAuthentication> authentication,
                                      Optional<Integer> exporterTimeoutMillis, Optional<Integer> exporterIntervalMillis,
                                      Optional<String> serviceName, Optional<String> serviceNamespace, Optional<String> disabledResourceProviders, Map<String, String> configurationProperties) {
        this.endpoint = endpoint.filter(StringUtils::isNotBlank);
        this.trustedCertificatesPem = trustedCertificatesPem.filter(StringUtils::isNotBlank);
        this.authentication = authentication;
        this.exporterTimeoutMillis = exporterTimeoutMillis;
        this.exporterIntervalMillis = exporterIntervalMillis;
        this.serviceName = serviceName.filter(StringUtils::isNotBlank);
        this.serviceNamespace = serviceNamespace.filter(StringUtils::isNotBlank);
        this.disabledResourceProviders = disabledResourceProviders.filter(StringUtils::isNotBlank);
        this.configurationProperties = configurationProperties;

        this.getEndpoint().ifPresent(ep ->
            Preconditions.checkArgument(
                ep.startsWith("http://") ||
                    ep.startsWith("https://"),
                "endpoint must be prefixed by 'http://' or 'https://': %s", ep));
    }

    public Optional<String> getEndpoint() {
        return endpoint;
    }

    public Optional<String> getServiceName() {
        return serviceName;
    }

    public Optional<String> getServiceNamespace() {
        return serviceNamespace;
    }

    public Optional<OtlpAuthentication> getAuthentication() {
        return authentication;
    }

    public Optional<String> getTrustedCertificatesPem() {
        return trustedCertificatesPem;
    }

    public Optional<Integer> getExporterTimeoutMillis() {
        return exporterTimeoutMillis;
    }

    public Optional<Integer> getExporterIntervalMillis() {
        return exporterIntervalMillis;
    }

    public Optional<String> getDisabledResourceProviders() {
        return disabledResourceProviders;
    }

    /**
     * @see io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder#addPropertiesSupplier(java.util.function.Supplier)
     */
    @NonNull
    public Map<String, String> toOpenTelemetryProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.putAll(this.configurationProperties);
        if (TESTING_INMEMORY_MODE) {
            properties.put("otel.traces.exporter", "testing");
            properties.put("otel.metrics.exporter", "testing");
            properties.put("otel.imr.export.interval", "10ms");
        } else if (this.getEndpoint().isPresent()) {
            this.getEndpoint().ifPresent(endpoint -> { // prepare of Optional.ifPResentOrElse()
                properties.put("otel.traces.exporter", "otlp");
                properties.put("otel.metrics.exporter", "otlp");
                properties.put("otel.exporter.otlp.endpoint", endpoint);
            });
        } else if (StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_TRACES_EXPORTER")) &&
            StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_EXPORTER_OTLP_ENDPOINT")) &&
            StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"))) {
            // Change default of "otel.traces.exporter" from "otlp" to "none" unless "otel.exporter.otlp.endpoint" or "otel.exporter.otlp.traces.endpoint" is defined
            properties.put("otel.traces.exporter", "none");
        }

        this.getTrustedCertificatesPem().ifPresent(
            trustedCertificatesPem -> properties.put("otel.exporter.otlp.certificate", trustedCertificatesPem));

        this.getAuthentication().ifPresent(authentication ->
            authentication.enrichOpenTelemetryAutoConfigureConfigProperties(properties));

        this.getExporterTimeoutMillis().map(Object::toString).ifPresent(exporterTimeoutMillis ->
            properties.put("otel.exporter.otlp.timeout", exporterTimeoutMillis));

        this.getExporterIntervalMillis().map(Object::toString).ifPresent(exporterIntervalMillis ->
            properties.put("otel.imr.export.interval", exporterIntervalMillis));

        this.getDisabledResourceProviders().ifPresent(disabledResourceProviders ->
            properties.put("otel.java.disabled.resource.providers", disabledResourceProviders));

        return properties;
    }

    /**
     * @see io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder#addResourceCustomizer(BiFunction)
     */
    @NonNull
    public Resource toOpenTelemetryResource() {
        ResourceBuilder resourceBuilder = Resource.builder();
        this.getServiceName().ifPresent(serviceName ->
            resourceBuilder.put(ResourceAttributes.SERVICE_NAME, serviceName));

        this.getServiceNamespace().ifPresent(serviceNamespace ->
            resourceBuilder.put(ResourceAttributes.SERVICE_NAMESPACE, serviceNamespace));

        resourceBuilder.put(JenkinsOtelSemanticAttributes.JENKINS_OPEN_TELEMETRY_PLUGIN_VERSION, OtelUtils.getOpentelemetryPluginVersion());

        return resourceBuilder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenTelemetryConfiguration that = (OpenTelemetryConfiguration) o;
        return Objects.equals(endpoint, that.endpoint) && Objects.equals(authentication, that.authentication) &&
            Objects.equals(trustedCertificatesPem, that.trustedCertificatesPem) && Objects.equals(exporterTimeoutMillis, that.exporterTimeoutMillis) &&
            Objects.equals(exporterIntervalMillis, that.exporterIntervalMillis) &&
            Objects.equals(serviceName, that.serviceName) && Objects.equals(serviceNamespace, that.serviceNamespace) &&
            Objects.equals(disabledResourceProviders, that.disabledResourceProviders) &&
            Objects.equals(configurationProperties, that.configurationProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoint, authentication, trustedCertificatesPem, exporterTimeoutMillis, exporterIntervalMillis, serviceName, serviceNamespace, disabledResourceProviders, configurationProperties);
    }

    @Override
    public String toString() {
        return "OpenTelemetryConfiguration{" +
            "endpoint='" + endpoint + '\'' +
            ", trustedCertificatesPem.defined=" + trustedCertificatesPem.isPresent() +
            ", authentication=" + authentication +
            ", exporterTimeoutMillis=" + exporterTimeoutMillis +
            ", exporterIntervalMillis=" + exporterIntervalMillis +
            ", serviceName=" + serviceName +
            ", serviceNamespace=" + serviceNamespace +
            ", disabledResourceProviders=" + disabledResourceProviders +
            '}';
    }
}
