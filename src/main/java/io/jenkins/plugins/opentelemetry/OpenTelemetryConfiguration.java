/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import static io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey.*;

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
        Map<String, String> properties = new HashMap<>(this.configurationProperties);
        if (TESTING_INMEMORY_MODE) {
            properties.put(OTEL_TRACES_EXPORTER.asProperty(), "testing");
            properties.put(OTEL_METRICS_EXPORTER.asProperty(), "testing");
            properties.put(OTEL_IMR_EXPORT_INTERVAL.asProperty(), "10ms");
        } else if (this.getEndpoint().isPresent()) {
            this.getEndpoint().ifPresent(endpoint -> { // prepare of Optional.ifPResentOrElse()
                properties.compute(OTEL_TRACES_EXPORTER.asProperty(), (key, oldValue) -> {
                    if (oldValue == null) {
                        return "otlp";
                    } else if ("none".equals(oldValue)) {
                        return "none";
                    } else if (oldValue.contains("otlp")) {
                        return oldValue;
                    } else {
                        return oldValue.concat(",otlp");
                    }
                });
                properties.compute(OTEL_METRICS_EXPORTER.asProperty(), (key, oldValue) -> {
                    if (oldValue == null) {
                        return "otlp";
                    } else if ("none".equals(oldValue)) {
                        return "none";
                    } else if (oldValue.contains("otlp")) {
                        return oldValue;
                    } else {
                        return oldValue.concat(",otlp");
                    }
                });
                properties.put(OTEL_EXPORTER_OTLP_ENDPOINT.asProperty(), endpoint);
            });
        } else if (StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_TRACES_EXPORTER")) &&
            StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_EXPORTER_OTLP_ENDPOINT")) &&
            StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"))) {
            // Change default of "otel.traces.exporter" from "otlp" to "none" unless "otel.exporter.otlp.endpoint" or "otel.exporter.otlp.traces.endpoint" is defined
            properties.put(OTEL_TRACES_EXPORTER.asProperty(), "none");
        }

        this.getTrustedCertificatesPem().ifPresent(
            trustedCertificatesPem -> properties.put(OTEL_EXPORTER_OTLP_CERTIFICATE.asProperty(), trustedCertificatesPem));

        this.getAuthentication().ifPresent(authentication ->
            authentication.enrichOpenTelemetryAutoConfigureConfigProperties(properties));

        this.getExporterTimeoutMillis().map(Object::toString).ifPresent(exporterTimeoutMillis ->
            properties.put(OTEL_EXPORTER_OTLP_TIMEOUT.asProperty(), exporterTimeoutMillis));

        this.getExporterIntervalMillis().map(Object::toString).ifPresent(exporterIntervalMillis ->
            properties.put(OTEL_IMR_EXPORT_INTERVAL.asProperty(), exporterIntervalMillis));

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
            resourceBuilder.put(ServiceAttributes.SERVICE_NAME, serviceName));

        this.getServiceNamespace().ifPresent(serviceNamespace ->
            resourceBuilder.put(ServiceIncubatingAttributes.SERVICE_NAMESPACE, serviceNamespace));

        resourceBuilder.put(JenkinsOtelSemanticAttributes.JENKINS_OPEN_TELEMETRY_PLUGIN_VERSION, OtelUtils.getOpentelemetryPluginVersion());

        return resourceBuilder.build();
    }
    /**
     * @see io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder#addResourceCustomizer(BiFunction)
     */
    @NonNull
    public Map<String, String> toOpenTelemetryResourceAsMap() {
        Map<String, String> resourceMap = new HashMap<>();
        this.getServiceName().ifPresent(serviceName ->
            resourceMap.put(ServiceAttributes.SERVICE_NAME.getKey(), serviceName));

        this.getServiceNamespace().ifPresent(serviceNamespace ->
            resourceMap.put(ServiceIncubatingAttributes.SERVICE_NAMESPACE.getKey(), serviceNamespace));

        resourceMap.put(JenkinsOtelSemanticAttributes.JENKINS_OPEN_TELEMETRY_PLUGIN_VERSION.getKey(), OtelUtils.getOpentelemetryPluginVersion());

        return resourceMap;
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
