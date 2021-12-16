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
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.extension.resources.ProcessResourceProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import jenkins.model.JenkinsLocationConfiguration;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
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

    protected transient SdkMeterProvider sdkMeterProvider;
    protected transient MeterProvider meterProvider;
    protected transient Meter meter;
    protected transient Resource resource;

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

        AutoConfiguredOpenTelemetrySdkBuilder sdkBuilder = AutoConfiguredOpenTelemetrySdk.builder();
        // PROPERTIES
        sdkBuilder.addPropertiesSupplier(() -> configuration.toOpenTelemetryProperties());

        // RESOURCE
        sdkBuilder.addResourceCustomizer((resource, configProperties) -> {
                ResourceBuilder resourceBuilder = Resource.builder()
                    .put(ResourceAttributes.SERVICE_VERSION, OtelUtils.getJenkinsVersion())
                    .put(JenkinsOtelSemanticAttributes.JENKINS_URL, jenkinsLocationConfiguration.getUrl())
                    .put(JenkinsOtelSemanticAttributes.JENKINS_OTEL_VERSION, OtelUtils.getOpentelemetryPluginVersion())
                    .putAll(resource)
                    .putAll(configuration.toOpenTelemetryResource());

            // mimic i.o.s.a.OpenTelemetryResourceAutoConfiguration.configureResource(ConfigProperties, BiFunction<? super Resource,ConfigProperties,? extends Resource>)
            // waiting for this feature to support specifying the classloader
            {
                Set<String> disabledProviders =
                    new HashSet<>(configProperties.getList("otel.java.disabled.resource.providers"));
                LOGGER.log(Level.FINER, () -> "Disabled providers: " + disabledProviders);
                ClassLoader serviceClassLoader =
                    AutoConfiguredOpenTelemetrySdkBuilder.class.getClassLoader();
                for (ResourceProvider resourceProvider : ServiceLoader.load(ResourceProvider.class, serviceClassLoader)) {
                    Resource extensionResources = resourceProvider.createResource(configProperties);
                    if (disabledProviders.contains(resourceProvider.getClass().getName())) {
                        continue;
                    }
                    LOGGER.log(Level.FINER, () -> "ResourceProvider: " + resourceProvider + " - add resources " + extensionResources.getAttributes().asMap().entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", ")));
                    resourceBuilder.putAll(extensionResources);
                }
            }
                return resourceBuilder.build();
            }
        );

        AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk = sdkBuilder.build();
        this.openTelemetrySdk = autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk();
        this.resource = autoConfiguredOpenTelemetrySdk.getResource();
        LOGGER.log(Level.FINE, () -> "OpenTelemetry resource: " +
            resource.getAttributes().asMap().entrySet().stream()
                .map(e -> e.getKey().getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));
        this.openTelemetry = this.openTelemetrySdk;
        this.tracer.setDelegate(openTelemetry.getTracer("jenkins"));

        this.meterProvider = GlobalMeterProvider.get();
        if (this.meterProvider instanceof SdkMeterProvider) {
            this.sdkMeterProvider = (SdkMeterProvider) this.meterProvider;
        } else {
            // The meterProvider created by the AutoConfiguredOpenTelemetrySdkBuilder is a NoopMeterProvider instead of a SdkMeterProvider if "otel.metrics.exporter=none"
            // See https://github.com/open-telemetry/opentelemetry-java/blob/v1.9.0/sdk-extensions/autoconfigure/src/main/java/io/opentelemetry/sdk/autoconfigure/OpenTelemetrySdkAutoConfiguration.java#L148
        }
        this.meter = this.meterProvider.get("jenkins");

        LOGGER.log(Level.INFO, () -> "OpenTelemetry initialized: " + ConfigPropertiesUtils.prettyPrintConfiguration(autoConfiguredOpenTelemetrySdk.getConfig()));
    }

    public void initializeNoOp() {
        LOGGER.log(Level.FINE, "initializeNoOp");
        preDestroy();

        this.openTelemetrySdk = null;
        this.resource = Resource.getDefault();
        this.openTelemetry = OpenTelemetry.noop();
        GlobalOpenTelemetry.resetForTest(); // hack for testing in Intellij cause by DiskUsageMonitoringInitializer
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
