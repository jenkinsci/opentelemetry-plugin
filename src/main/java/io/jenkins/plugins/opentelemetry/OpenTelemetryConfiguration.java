/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey.*;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import org.apache.commons.lang3.StringUtils;

/**
 * Holds the OpenTelemetry configuration for the Jenkins plugin, including
 * the OTLP endpoint, authentication, service identity, and additional
 * SDK configuration properties.
 *
 * <p>An instance of this class is constructed from the Jenkins plugin
 * configuration UI and converted into OpenTelemetry SDK properties via
 * {@link #toOpenTelemetryProperties()} and resource attributes via
 * {@link #toOpenTelemetryResource()}.</p>
 */
public class OpenTelemetryConfiguration {

    @SuppressFBWarnings
    @VisibleForTesting
    public static boolean TESTING_INMEMORY_MODE = false;

    private final Optional<String> endpoint;
    private final Optional<String> trustedCertificatesPem;
    private final Optional<OtlpAuthentication> authentication;
    private final Optional<String> serviceName;
    private final Optional<String> serviceNamespace;
    private final Optional<String> disabledResourceProviders;
    private final Map<String, String> configurationProperties;

    /**
     * Constructs a new {@code OpenTelemetryConfiguration} with the given settings.
     *
     * <p>Blank strings in {@code Optional} fields are treated as empty
     * (i.e., the field is considered not configured). The endpoint, if present,
     * must start with {@code http://} or {@code https://}.</p>
     *
     * @param endpoint                  the OTLP endpoint URL, e.g. {@code http://otel-collector:4317}
     * @param trustedCertificatesPem    PEM-encoded trusted TLS certificate for the OTLP endpoint
     * @param authentication            the authentication mechanism for the OTLP endpoint
     * @param serviceName               the {@code service.name} OpenTelemetry resource attribute
     * @param serviceNamespace          the {@code service.namespace} OpenTelemetry resource attribute
     * @param disabledResourceProviders comma-separated list of resource provider class names to disable
     * @param configurationProperties   additional OpenTelemetry SDK autoconfigure properties
     * @throws IllegalArgumentException if the endpoint is present but does not start with
     *                                  {@code http://} or {@code https://}
     */
    public OpenTelemetryConfiguration(
            Optional<String> endpoint,
            Optional<String> trustedCertificatesPem,
            Optional<OtlpAuthentication> authentication,
            Optional<String> serviceName,
            Optional<String> serviceNamespace,
            Optional<String> disabledResourceProviders,
            Map<String, String> configurationProperties) {
        this.endpoint = endpoint.filter(StringUtils::isNotBlank);
        this.trustedCertificatesPem = trustedCertificatesPem.filter(StringUtils::isNotBlank);
        this.authentication = authentication;
        this.serviceName = serviceName.filter(StringUtils::isNotBlank);
        this.serviceNamespace = serviceNamespace.filter(StringUtils::isNotBlank);
        this.disabledResourceProviders = disabledResourceProviders.filter(StringUtils::isNotBlank);
        this.configurationProperties = configurationProperties;

        this.getEndpoint()
                .ifPresent(ep -> Preconditions.checkArgument(
                        ep.startsWith("http://") || ep.startsWith("https://"),
                        "endpoint must be prefixed by 'http://' or 'https://': %s",
                        ep));
    }

    /**
     * Returns the OTLP endpoint URL configured for this Jenkins instance.
     *
     * <p>The endpoint must include the protocol scheme, for example
     * {@code http://otel-collector:4317} for gRPC or
     * {@code http://otel-collector:4318} for HTTP/Protobuf.
     * Returns an empty {@code Optional} if no endpoint is configured or
     * if the configured value is blank.</p>
     *
     * @return an {@code Optional} containing the OTLP endpoint URL,
     *         or {@code Optional.empty()} if not configured
     */
    public Optional<String> getEndpoint() {
        return endpoint;
    }

    /**
     * Returns the {@code service.name} OpenTelemetry resource attribute.
     *
     * <p>This value appears as the service name in traces and metrics in
     * your observability backend (e.g. Jaeger, Elastic, Prometheus).
     * Defaults to {@code jenkins} if not explicitly configured.</p>
     *
     * @return an {@code Optional} containing the service name,
     *         or {@code Optional.empty()} if not configured
     */
    public Optional<String> getServiceName() {
        return serviceName;
    }

    /**
     * Returns the {@code service.namespace} OpenTelemetry resource attribute.
     *
     * <p>Used to group related services in your observability backend.
     * For example, setting this to {@code ci} groups Jenkins alongside
     * other CI/CD tools emitting telemetry to the same backend.</p>
     *
     * @return an {@code Optional} containing the service namespace,
     *         or {@code Optional.empty()} if not configured
     */
    public Optional<String> getServiceNamespace() {
        return serviceNamespace;
    }

    /**
     * Returns the authentication configuration used when connecting to the
     * OTLP endpoint.
     *
     * <p>Supported implementations include no authentication, header-based
     * authentication, and Bearer token authentication. See
     * {@link OtlpAuthentication} for available options.</p>
     *
     * @return an {@code Optional} containing the authentication configuration,
     *         or {@code Optional.empty()} if no authentication is configured
     */
    public Optional<OtlpAuthentication> getAuthentication() {
        return authentication;
    }

    /**
     * Returns the PEM-encoded trusted TLS certificate for the OTLP endpoint.
     *
     * <p>Use this when the OTLP endpoint uses a TLS certificate signed by a
     * private or internal CA that is not trusted by the default JVM truststore.
     * The value should be the full PEM certificate string including the
     * {@code -----BEGIN CERTIFICATE-----} header and footer.</p>
     *
     * @return an {@code Optional} containing the PEM certificate string,
     *         or {@code Optional.empty()} if no custom certificate is configured
     */
    public Optional<String> getTrustedCertificatesPem() {
        return trustedCertificatesPem;
    }

    /**
     * Returns the comma-separated list of OpenTelemetry Java resource provider
     * class names that should be disabled.
     *
     * <p>Maps to the {@code otel.java.disabled.resource.providers} SDK property.
     * Use this to suppress resource attributes contributed by specific providers,
     * for example to avoid slow classpath scanning on large Jenkins instances.</p>
     *
     * @return an {@code Optional} containing the comma-separated provider class names,
     *         or {@code Optional.empty()} if no providers are disabled
     */
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
            properties.putIfAbsent(OTEL_TRACES_EXPORTER.asProperty(), "testing");
            properties.putIfAbsent(OTEL_METRICS_EXPORTER.asProperty(), "testing");
            properties.putIfAbsent(OTEL_METRIC_EXPORT_INTERVAL.asProperty(), "10ms");
            properties.putIfAbsent(OTEL_LOGS_EXPORTER.asProperty(), "none");
        } else if (this.getEndpoint().isPresent()) {
            this.getEndpoint()
                    .ifPresent(
                            endpoint -> { // prepare of Optional.ifPResentOrElse()
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
        } else if (StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_TRACES_EXPORTER"))
                && StringUtils.isBlank(OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_EXPORTER_OTLP_ENDPOINT"))
                && StringUtils.isBlank(
                        OtelUtils.getSystemPropertyOrEnvironmentVariable("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"))) {
            // Change default of "otel.traces.exporter" from "otlp" to "none" unless "otel.exporter.otlp.endpoint" or
            // "otel.exporter.otlp.traces.endpoint" is defined
            properties.put(OTEL_TRACES_EXPORTER.asProperty(), "none");
            properties.put(OTEL_METRICS_EXPORTER.asProperty(), "none");
            properties.put(OTEL_LOGS_EXPORTER.asProperty(), "none");
        }

        this.getTrustedCertificatesPem()
                .ifPresent(trustedCertificatesPem ->
                        properties.put(OTEL_EXPORTER_OTLP_CERTIFICATE.asProperty(), trustedCertificatesPem));

        this.getAuthentication()
                .ifPresent(
                        authentication -> authentication.enrichOpenTelemetryAutoConfigureConfigProperties(properties));

        this.getDisabledResourceProviders()
                .ifPresent(disabledResourceProviders ->
                        properties.put(OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS.asProperty(), disabledResourceProviders));

        return properties;
    }

    /**
     * @see io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder#addResourceCustomizer(BiFunction)
     */
    @NonNull
    public Resource toOpenTelemetryResource() {
        ResourceBuilder resourceBuilder = Resource.builder();
        this.getServiceName()
                .ifPresent(serviceName -> resourceBuilder.put(ServiceAttributes.SERVICE_NAME, serviceName));

        this.getServiceNamespace()
                .ifPresent(serviceNamespace ->
                        resourceBuilder.put(ServiceIncubatingAttributes.SERVICE_NAMESPACE, serviceNamespace));

        resourceBuilder.put(
                ExtendedJenkinsAttributes.JENKINS_OPEN_TELEMETRY_PLUGIN_VERSION,
                OtelUtils.getOpentelemetryPluginVersion());

        return resourceBuilder.build();
    }
    /**
     * @see io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder#addResourceCustomizer(BiFunction)
     */
    @NonNull
    public Map<String, String> toOpenTelemetryResourceAsMap() {
        Map<String, String> resourceMap = new HashMap<>();
        this.getServiceName()
                .ifPresent(serviceName -> resourceMap.put(ServiceAttributes.SERVICE_NAME.getKey(), serviceName));

        this.getServiceNamespace()
                .ifPresent(serviceNamespace ->
                        resourceMap.put(ServiceIncubatingAttributes.SERVICE_NAMESPACE.getKey(), serviceNamespace));

        resourceMap.put(
                ExtendedJenkinsAttributes.JENKINS_OPEN_TELEMETRY_PLUGIN_VERSION.getKey(),
                OtelUtils.getOpentelemetryPluginVersion());

        return resourceMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenTelemetryConfiguration that = (OpenTelemetryConfiguration) o;
        return Objects.equals(endpoint, that.endpoint)
                && Objects.equals(authentication, that.authentication)
                && Objects.equals(trustedCertificatesPem, that.trustedCertificatesPem)
                && Objects.equals(serviceName, that.serviceName)
                && Objects.equals(serviceNamespace, that.serviceNamespace)
                && Objects.equals(disabledResourceProviders, that.disabledResourceProviders)
                && Objects.equals(configurationProperties, that.configurationProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                endpoint,
                authentication,
                trustedCertificatesPem,
                serviceName,
                serviceNamespace,
                disabledResourceProviders,
                configurationProperties);
    }

    @Override
    public String toString() {
        return "OpenTelemetryConfiguration{" + "endpoint='"
                + endpoint + '\'' + ", trustedCertificatesPem.defined="
                + trustedCertificatesPem.isPresent() + ", authentication="
                + authentication + ", serviceName="
                + serviceName + ", serviceNamespace="
                + serviceNamespace + ", disabledResourceProviders="
                + disabledResourceProviders + '}';
    }
}