/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static io.jenkins.plugins.opentelemetry.OtelUtils.UNKNOWN;
import static io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend.ICONS_PREFIX;
import static io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey.OTEL_EXPORTER_OTLP_CERTIFICATE;
import static io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey.OTEL_EXPORTER_OTLP_ENDPOINT;
import static io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey.OTEL_EXPORTER_OTLP_INSECURE;
import static io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey.OTEL_EXPORTER_OTLP_TIMEOUT;
import static io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_EXPORT_OTEL_CONFIG_AS_ENV_VARS;
import static io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey.OTEL_METRIC_EXPORT_INTERVAL;
import static io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey.OTEL_TRACES_EXPORTER;

import com.google.common.base.Strings;
import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import groovy.text.GStringTemplateEngine;
import hudson.Extension;
import hudson.PluginWrapper;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.init.Terminator;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.tasks.BuildStep;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.authentication.NoAuthentication;
import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.backend.custom.CustomLogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import jenkins.model.CauseOfInterruption;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import net.jcip.annotations.Immutable;
import net.sf.json.JSONObject;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.CoreStep;
import org.jenkinsci.plugins.workflow.support.steps.StageStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

@Extension(
        ordinal =
                Integer.MAX_VALUE
                        - 1 /* initialize OTel ASAP, just after loading JenkinsControllerOpenTelemetry as GlobalOpenTelemetry */)
@Symbol("openTelemetry")
/**
 * Global Jenkins configuration for the OpenTelemetry plugin.
 * <p>
 * Manages OTLP endpoint, authentication, observability backends, pipeline step ignore lists,
 * resource attributes, and SDK lifecycle configuration.
 */
public class JenkinsOpenTelemetryPluginConfiguration extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(JenkinsOpenTelemetryPluginConfiguration.class.getName());

    static {
        IconSet.icons.addIcon(new Icon("icon-otel icon-sm", ICONS_PREFIX + "opentelemetry.svg", Icon.ICON_SMALL_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel icon-md", ICONS_PREFIX + "opentelemetry.svg", Icon.ICON_MEDIUM_STYLE));
        IconSet.icons.addIcon(new Icon("icon-otel icon-lg", ICONS_PREFIX + "opentelemetry.svg", Icon.ICON_LARGE_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel icon-xlg", ICONS_PREFIX + "opentelemetry.svg", Icon.ICON_XLARGE_STYLE));
    }

    /**
     * OTLP endpoint prefixed by "http://" or "https://"
     */
    private String endpoint;

    private String trustedCertificatesPem;

    private OtlpAuthentication authentication;

    private List<ObservabilityBackend> observabilityBackends = new ArrayList<>();

    @Deprecated
    private Integer exporterTimeoutMillis = null;

    @Deprecated
    private Integer exporterIntervalMillis = null;

    private String ignoredSteps = "dir,echo,isUnix,pwd,properties";

    private String disabledResourceProviders =
            JenkinsControllerOpenTelemetry.DEFAULT_OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS;

    @Inject
    private transient ReconfigurableOpenTelemetry openTelemetry;

    private transient LogStorageRetriever logStorageRetriever;

    private boolean exportOtelConfigurationAsEnvironmentVariables;

    private final transient ConcurrentMap<String, StepPlugin> loadedStepsPlugins = new ConcurrentHashMap<>();

    private String configurationProperties;

    private String serviceName;

    private String serviceNamespace;

    /**
     * Interruption causes that should mark the span as error because they are external interruptions.
     * <p>
     * TODO make this list configurable and accessible through {@link io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties#getList(String)}
     *
     * @see CauseOfInterruption
     * @see org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
     */
    private final List<String> statusUnsetCausesOfInterruption = Arrays.asList(
            "org.jenkinsci.plugins.workflow.cps.steps.ParallelStep$FailFastCause",
            StageStepExecution.CanceledCause.class.getName(),
            CauseOfInterruption.UserInterruption.class.getName());

    /**
     * The previously used configuration. Kept in memory to prevent unneeded reconfigurations.
     */
    protected transient OpenTelemetryConfiguration currentOpenTelemetryConfiguration;

    /**
     * Creates global plugin configuration bound from persisted data.
     */
    @DataBoundConstructor
    public JenkinsOpenTelemetryPluginConfiguration() {
        load();
    }

    // needed by CloudBees HA see https://github.com/jenkinsci/opentelemetry-plugin/issues/1156
    /**
     * Reloads persisted configuration and reconfigures SDK if already initialized.
     */
    @Override
    public void load() {
        super.load();
        if (currentOpenTelemetryConfiguration != null) {
            // After reloading the XML configuration, we need to reconfigure the OTel SDK, otherwise the fields here
            // may be out of sync with the SDK. We only do this as long as `configureOpenTelemetrySdk` has run at least
            // once so that the first configuration happens during startup via `@Initializer` after applying CasC.
            configureOpenTelemetrySdk();
        }
    }

    /**
     * Binds Jenkins global-configuration form data and applies SDK reconfiguration.
     *
     * @param req stapler request
     * @param json submitted JSON payload
     * @return {@code true} when configuration is accepted
     * @throws FormException if SDK configuration fails
     */
    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        LOGGER.log(Level.FINE, "Configure...");
        req.bindJSON(this, json);
        // stapler oddity, empty lists coming from the HTTP request are not set on bean by  `req.bindJSON(this, json)`
        this.observabilityBackends = req.bindJSONToList(ObservabilityBackend.class, json.get("observabilityBackends"));
        this.endpoint = sanitizeOtlpEndpoint(this.endpoint);
        try {
            configureOpenTelemetrySdk();
            save();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Exception configuring OpenTelemetry SDK", e);
            throw new FormException("Exception configuring OpenTelemetry SDK: " + e.getMessage(), e, "endpoint");
        }
        LOGGER.log(Level.FINE, "Configured");
        return true;
    }

    /**
     * Migrates legacy fields while restoring configuration state from XML.
     *
     * @return this configuration after migration
     */
    protected Object readResolve() {
        LOGGER.log(Level.FINE, "readResolve()");
        boolean configModified = false;
        if (this.disabledResourceProviders == null) {
            this.disabledResourceProviders =
                    JenkinsControllerOpenTelemetry.DEFAULT_OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS;
            LOGGER.log(Level.INFO, "Migration of the 'disabledResourceProviders' config param");
            configModified = true;
        }
        if (this.exporterTimeoutMillis != null) {
            this.configurationProperties = this.configurationProperties + "\n"
                    + "# Migration of the 'exporterTimeoutMillis' config param to 'otel.exporter.otlp.timeout' property\n"
                    + OTEL_EXPORTER_OTLP_TIMEOUT.asProperty()
                    + "=" + this.exporterTimeoutMillis;
            this.exporterTimeoutMillis = null;
            LOGGER.log(
                    Level.INFO,
                    "Migration of the 'exporterTimeoutMillis' config param to 'otel.exporter.otlp.timeout' property");
            configModified = true;
        }
        if (this.exporterIntervalMillis != null) {
            this.configurationProperties = this.configurationProperties + "\n"
                    + "# Migration of the 'exporterIntervalMillis' config param to 'otel.metric.export.interval' property\n"
                    + OTEL_METRIC_EXPORT_INTERVAL.asProperty()
                    + "=" + this.exporterIntervalMillis;
            this.exporterIntervalMillis = null;
            LOGGER.log(
                    Level.INFO,
                    "Migration of the 'exporterIntervalMillis' config param to 'otel.metric.export.interval' property");
            configModified = true;
        }

        if (configModified) {
            save();
        }
        return this;
    }

    /**
     * Builds runtime OpenTelemetry configuration from Jenkins global settings.
     *
     * @return aggregated OpenTelemetry configuration
     */
    @NonNull
    public OpenTelemetryConfiguration toOpenTelemetryConfiguration() {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(Objects.toString(this.configurationProperties)));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Exception parsing configuration properties", e);
        }

        Map<String, String> configurationProperties = new HashMap<>();
        getObservabilityBackends()
                .forEach(backend -> configurationProperties.putAll(backend.getOtelConfigurationProperties()));
        configurationProperties.put(ExtendedJenkinsAttributes.JENKINS_VERSION.getKey(), OtelUtils.getJenkinsVersion());
        configurationProperties.put(
                ExtendedJenkinsAttributes.JENKINS_URL.getKey(), this.jenkinsLocationConfiguration.getUrl());
        configurationProperties.put(
                OTEL_INSTRUMENTATION_JENKINS_EXPORT_OTEL_CONFIG_AS_ENV_VARS.asProperty(),
                Boolean.toString(this.exportOtelConfigurationAsEnvironmentVariables));

        // use same Jenkins instance identifier as the Jenkins Support Core plugin. No need to add the complexity of the
        // instance-identity-plugin
        // https://github.com/jenkinsci/support-core-plugin/blob/support-core-2.81/src/main/java/com/cloudbees/jenkins/support/impl/AboutJenkins.java#L401
        configurationProperties.put(
                ServiceIncubatingAttributes.SERVICE_INSTANCE_ID.getKey(),
                Jenkins.get().getLegacyInstanceId());
        properties.forEach(
                (k, v) -> configurationProperties.put(Objects.toString(k, "#null#"), Objects.toString(v, "#null#")));

        return new OpenTelemetryConfiguration(
                Optional.ofNullable(this.getEndpoint()),
                Optional.ofNullable(this.getTrustedCertificatesPem()),
                Optional.of(this.getAuthentication()),
                Optional.ofNullable(this.getServiceName()),
                Optional.ofNullable(this.getServiceNamespace()),
                Optional.ofNullable(this.getDisabledResourceProviders()),
                configurationProperties);
    }

    /**
     * Initialize the Otel SDK, must happen after the plugin has been configured by the standard config and by JCasC
     * JCasC configuration happens during `SYSTEM_CONFIG_ADAPTED` (see `io.jenkins.plugins.casc.ConfigurationAsCode#init()`)
     */
    @Initializer(after = InitMilestone.SYSTEM_CONFIG_ADAPTED, before = InitMilestone.JOB_LOADED)
    @SuppressWarnings("MustBeClosedChecker")
    public void configureOpenTelemetrySdk() {
        LOGGER.log(Level.FINE, "Configure OpenTelemetry SDK...");

        OpenTelemetryConfiguration newOpenTelemetryConfiguration = toOpenTelemetryConfiguration();
        if (Objects.equals(this.currentOpenTelemetryConfiguration, newOpenTelemetryConfiguration)) {
            LOGGER.log(Level.FINE, "Configuration didn't change, skip reconfiguration");
        } else {
            openTelemetry.configure(
                    newOpenTelemetryConfiguration.toOpenTelemetryProperties(),
                    newOpenTelemetryConfiguration.toOpenTelemetryResource(),
                    true);
            this.currentOpenTelemetryConfiguration = newOpenTelemetryConfiguration;
            closeLogStorageRetriever();
            this.logStorageRetriever = resolveLogStorageRetriever();
        }
    }

    /**
     * Close the currently setup {@link LogStorageRetriever} if it is {@link Closeable}.
     */
    private void closeLogStorageRetriever() {
        if (logStorageRetriever != null && logStorageRetriever instanceof Closeable) {
            LOGGER.log(Level.FINE, () -> "Close " + logStorageRetriever + "...");
            try {
                ((Closeable) logStorageRetriever).close();
            } catch (IOException e) {
                LOGGER.log(
                        Level.WARNING,
                        "Exception closing currently setup logStorageRetriever: " + logStorageRetriever,
                        e);
            }
        }
    }

    /**
     * @return {@code null} or endpoint URI prefixed by a protocol scheme ("http://", "https://"...)
     */
    @CheckForNull
    public String sanitizeOtlpEndpoint(@Nullable String grpcEndpoint) {
        if (Strings.isNullOrEmpty(grpcEndpoint)) {
            return null;
        } else if (grpcEndpoint.contains("://")) {
            return grpcEndpoint;
        } else {
            return "http://" + grpcEndpoint;
        }
    }

    /**
     * Never empty
     */
    @CheckForNull
    public String getEndpoint() {
        return sanitizeOtlpEndpoint(this.endpoint);
    }

    /**
     * Sets OTLP endpoint value after normalization.
     *
     * @param endpoint OTLP endpoint as entered in Jenkins UI
     */
    @DataBoundSetter
    public void setEndpoint(String endpoint) {
        this.endpoint = sanitizeOtlpEndpoint(endpoint);
        // debug line used to verify the lifecycle (@Initializer) when using JCasC configuration
        LOGGER.log(Level.FINE, () -> "setEndpoint(" + endpoint + ")");
    }

    /**
     * Returns configured OTLP authentication strategy.
     *
     * @return authentication strategy, defaults to {@link NoAuthentication}
     */
    @NonNull
    public OtlpAuthentication getAuthentication() {
        return this.authentication == null ? new NoAuthentication() : this.authentication;
    }

    /**
     * Sets OTLP authentication strategy.
     *
     * @param authentication authentication strategy
     */
    @DataBoundSetter
    public void setAuthentication(OtlpAuthentication authentication) {
        this.authentication = authentication;
    }

    /**
     * Returns configured trusted certificates PEM.
     *
     * @return PEM content, or {@code null}
     */
    @CheckForNull
    public String getTrustedCertificatesPem() {
        return trustedCertificatesPem;
    }

    /**
     * Sets trusted certificates PEM.
     *
     * @param trustedCertificatesPem PEM content
     */
    @DataBoundSetter
    public void setTrustedCertificatesPem(String trustedCertificatesPem) {
        this.trustedCertificatesPem = trustedCertificatesPem;
    }

    /**
     * Sets configured observability backends.
     *
     * @param observabilityBackends configured backend list
     */
    @DataBoundSetter
    public void setObservabilityBackends(List<ObservabilityBackend> observabilityBackends) {
        this.observabilityBackends = observabilityBackends == null ? Collections.emptyList() : observabilityBackends;
    }

    /**
     * Returns configured observability backends.
     *
     * @return mutable backend list
     */
    @NonNull
    public List<ObservabilityBackend> getObservabilityBackends() {
        if (observabilityBackends == null) {
            observabilityBackends = new ArrayList<>();
        }
        return observabilityBackends;
    }

    /**
     * Returns legacy exporter timeout configuration.
     *
     * @return timeout in milliseconds, or {@code null}
     * @deprecated migrated to generic properties map
     */
    @Deprecated
    public Integer getExporterTimeoutMillis() {
        return exporterTimeoutMillis;
    }

    /**
     * Sets legacy exporter timeout configuration.
     *
     * @param exporterTimeoutMillis timeout in milliseconds
     * @deprecated migrated to generic properties map
     */
    @Deprecated
    @DataBoundSetter
    public void setExporterTimeoutMillis(Integer exporterTimeoutMillis) {
        this.exporterTimeoutMillis = exporterTimeoutMillis;
    }

    /**
     * Returns legacy exporter interval configuration.
     *
     * @return interval in milliseconds, or {@code null}
     * @deprecated migrated to generic properties map
     */
    @Deprecated
    public Integer getExporterIntervalMillis() {
        return exporterIntervalMillis;
    }

    /**
     * Sets legacy exporter interval configuration.
     *
     * @param exporterIntervalMillis interval in milliseconds
     * @deprecated migrated to generic properties map
     */
    @DataBoundSetter
    public void setExporterIntervalMillis(Integer exporterIntervalMillis) {
        this.exporterIntervalMillis = exporterIntervalMillis;
    }

    /**
     * Returns comma-separated pipeline steps ignored by span creation.
     *
     * @return ignored steps string
     */
    public String getIgnoredSteps() {
        return ignoredSteps;
    }

    /**
     * Sets comma-separated pipeline steps ignored by span creation.
     *
     * @param ignoredSteps ignored steps string
     */
    @DataBoundSetter
    public void setIgnoredSteps(String ignoredSteps) {
        this.ignoredSteps = ignoredSteps;
    }

    /**
     * Returns interruption causes mapped to span status {@code UNSET}.
     *
     * @return immutable list of interruption cause class names
     */
    public List<String> getStatusUnsetCausesOfInterruption() {
        return statusUnsetCausesOfInterruption;
    }

    /**
     * Returns disabled OpenTelemetry resource providers.
     *
     * @return disabled provider list string
     */
    public String getDisabledResourceProviders() {
        return disabledResourceProviders;
    }

    /**
     * Sets disabled OpenTelemetry resource providers.
     *
     * @param disabledResourceProviders disabled provider list string
     */
    @DataBoundSetter
    public void setDisabledResourceProviders(String disabledResourceProviders) {
        this.disabledResourceProviders = disabledResourceProviders;
    }

    /**
     * Returns whether OTEL config should be exported as environment variables.
     *
     * @return {@code true} when env var export is enabled
     */
    public boolean isExportOtelConfigurationAsEnvironmentVariables() {
        return exportOtelConfigurationAsEnvironmentVariables;
    }

    /**
     * Sets whether OTEL config should be exported as environment variables.
     *
     * @param exportOtelConfigurationAsEnvironmentVariables enable flag
     */
    @DataBoundSetter
    public void setExportOtelConfigurationAsEnvironmentVariables(
            boolean exportOtelConfigurationAsEnvironmentVariables) {
        this.exportOtelConfigurationAsEnvironmentVariables = exportOtelConfigurationAsEnvironmentVariables;
    }

    /**
     * Returns raw additional configuration properties.
     *
     * @return multi-line properties text
     */
    public String getConfigurationProperties() {
        return configurationProperties;
    }

    /**
     * Sets raw additional configuration properties.
     *
     * @param configurationProperties multi-line properties text
     */
    @DataBoundSetter
    public void setConfigurationProperties(String configurationProperties) {
        this.configurationProperties = configurationProperties;
    }

    /**
     * Builds OpenTelemetry-related environment variables from current plugin configuration.
     *
     * @return map of environment variable names to values
     */
    @NonNull
    public Map<String, String> getOtelConfigurationAsEnvironmentVariables() {
        if (this.endpoint == null) {
            return Collections.emptyMap();
        }

        Map<String, String> environmentVariables = new HashMap<>();
        environmentVariables.put(OTEL_TRACES_EXPORTER.asEnvVar(), "otlp");
        environmentVariables.put(OTEL_EXPORTER_OTLP_ENDPOINT.asEnvVar(), this.endpoint);
        String sanitizeOtlpEndpoint = sanitizeOtlpEndpoint(this.endpoint);
        if (sanitizeOtlpEndpoint != null && sanitizeOtlpEndpoint.startsWith("http://")) {
            environmentVariables.put(OTEL_EXPORTER_OTLP_INSECURE.asEnvVar(), Boolean.TRUE.toString());
        }
        this.authentication.enrichOtelEnvironmentVariables(environmentVariables);
        String trustedCertificatesPem = this.getTrustedCertificatesPem();
        if (trustedCertificatesPem != null && !trustedCertificatesPem.isEmpty()) {
            environmentVariables.put(OTEL_EXPORTER_OTLP_CERTIFICATE.asEnvVar(), trustedCertificatesPem);
        }
        if (this.exporterTimeoutMillis != null) {
            environmentVariables.put(
                    OTEL_EXPORTER_OTLP_TIMEOUT.asEnvVar(), Integer.toString(this.exporterTimeoutMillis));
        }
        return environmentVariables;
    }

    private JenkinsLocationConfiguration jenkinsLocationConfiguration;

    /**
     * Injects Jenkins location configuration used to publish Jenkins URL attributes.
     *
     * @param jenkinsLocationConfiguration Jenkins location configuration
     */
    @Inject
    public void setJenkinsLocationConfiguration(@NonNull JenkinsLocationConfiguration jenkinsLocationConfiguration) {
        this.jenkinsLocationConfiguration = jenkinsLocationConfiguration;
    }

    /**
     * For visualisation in config.jelly
     */
    @NonNull
    public String getVisualisationObservabilityBackendsString() {
        return "Visualisation observability backends: "
                + ObservabilityBackend.allDescriptors().stream()
                        .sorted()
                        .map(Descriptor::getDisplayName)
                        .collect(Collectors.joining(", "));
    }

    /**
     * Returns cache of discovered pipeline step plugins keyed by step symbol.
     *
     * @return loaded step plugin metadata map
     */
    @NonNull
    public ConcurrentMap<String, StepPlugin> getLoadedStepsPlugins() {
        return loadedStepsPlugins;
    }

    /**
     * Adds discovered plugin metadata for a step symbol.
     *
     * @param stepName step symbol
     * @param c plugin metadata
     */
    public void addStepPlugin(String stepName, StepPlugin c) {
        loadedStepsPlugins.put(stepName, c);
    }

    @Nullable
    private Descriptor<? extends Describable<?>> getStepDescriptor(
            @NonNull FlowNode node, @Nullable Descriptor<? extends Describable<?>> descriptor) {
        // Support for https://javadoc.jenkins.io/jenkins/tasks/SimpleBuildStep.html
        if (descriptor instanceof CoreStep.DescriptorImpl) {
            Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(node);
            if (arguments.get("delegate") instanceof UninstantiatedDescribable) {
                UninstantiatedDescribable describable = (UninstantiatedDescribable) arguments.get("delegate");
                if (describable != null) {
                    return SymbolLookup.get().findDescriptor(Describable.class, describable.getSymbol());
                }
            }
        }
        return descriptor;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private Descriptor<? extends Describable<?>> getBuildStepDescriptor(@NonNull BuildStep buildStep) {
        return Jenkins.get().getDescriptor((Class<? extends Describable<?>>) buildStep.getClass());
    }

    /**
     * Resolves plugin metadata for a freestyle build step.
     *
     * @param buildStepName step symbol
     * @param buildStep build step instance
     * @return resolved plugin metadata or default placeholder
     */
    @NonNull
    public StepPlugin findStepPluginOrDefault(@NonNull String buildStepName, @NonNull BuildStep buildStep) {
        return findStepPluginOrDefault(buildStepName, getBuildStepDescriptor(buildStep));
    }

    /**
     * Resolves plugin metadata for an atomic pipeline step.
     *
     * @param stepName step symbol
     * @param node pipeline step node
     * @return resolved plugin metadata or default placeholder
     */
    @NonNull
    public StepPlugin findStepPluginOrDefault(@NonNull String stepName, @NonNull StepAtomNode node) {
        return findStepPluginOrDefault(stepName, getStepDescriptor(node, node.getDescriptor()));
    }

    /**
     * Resolves plugin metadata for a step start node.
     *
     * @param stepName step symbol
     * @param node pipeline step start node
     * @return resolved plugin metadata or default placeholder
     */
    @NonNull
    public StepPlugin findStepPluginOrDefault(@NonNull String stepName, @NonNull StepStartNode node) {
        return findStepPluginOrDefault(stepName, getStepDescriptor(node, node.getDescriptor()));
    }

    /**
     * Resolves plugin metadata for a step symbol from descriptor information.
     *
     * @param stepName step symbol
     * @param descriptor step descriptor, when available
     * @return resolved plugin metadata or default placeholder
     */
    @NonNull
    public StepPlugin findStepPluginOrDefault(
            @NonNull String stepName, @Nullable Descriptor<? extends Describable<?>> descriptor) {
        StepPlugin data = loadedStepsPlugins.get(stepName);
        if (data != null) {
            LOGGER.log(Level.FINEST, " found the plugin for the step '" + stepName + "' - " + data);
            return data;
        }

        data = new StepPlugin();
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null && descriptor != null) {
            PluginWrapper wrapper = jenkins.getPluginManager().whichPlugin(descriptor.clazz);
            if (wrapper != null) {
                data = new StepPlugin(wrapper.getShortName(), wrapper.getVersion());
                addStepPlugin(stepName, data);
            }
        }
        return data;
    }

    /**
     * Resolves symbol for a freestyle build step descriptor.
     *
     * @param buildStepName fallback symbol
     * @param buildStep build step instance
     * @return resolved symbol or fallback
     */
    @NonNull
    public String findSymbolOrDefault(@NonNull String buildStepName, @NonNull BuildStep buildStep) {
        return findSymbolOrDefault(buildStepName, getBuildStepDescriptor(buildStep));
    }

    /**
     * Resolves symbol for a descriptor-backed build step.
     *
     * @param buildStepName fallback symbol
     * @param descriptor descriptor to inspect for symbol metadata
     * @return resolved symbol or fallback
     */
    @NonNull
    public String findSymbolOrDefault(
            @NonNull String buildStepName, @Nullable Descriptor<? extends Describable<?>> descriptor) {
        String value = buildStepName;
        if (descriptor != null) {
            Set<String> values = SymbolLookup.getSymbolValue(descriptor);
            value = values.stream().findFirst().orElse(buildStepName);
        }
        return value;
    }

    /**
     * @see io.opentelemetry.semconv.ServiceAttributes#SERVICE_NAME
     */
    public String getServiceName() {
        return (Strings.isNullOrEmpty(this.serviceName)) ? ExtendedJenkinsAttributes.JENKINS : this.serviceName;
    }

    /**
     * Sets configured OpenTelemetry service name.
     *
     * @param serviceName service name
     */
    @DataBoundSetter
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * @see io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes#SERVICE_NAMESPACE
     */
    public String getServiceNamespace() {
        return (Strings.isNullOrEmpty(this.serviceNamespace))
                ? ExtendedJenkinsAttributes.JENKINS
                : this.serviceNamespace;
    }

    /**
     * Sets configured OpenTelemetry service namespace.
     *
     * @param serviceNamespace service namespace
     */
    @DataBoundSetter
    public void setServiceNamespace(String serviceNamespace) {
        this.serviceNamespace = serviceNamespace;
    }

    /**
     * Returns resource attributes currently associated with the OpenTelemetry SDK.
     *
     * @return SDK resource attributes, or empty resource when unavailable
     */
    @NonNull
    public Resource getResource() {
        if (this.openTelemetry == null) {
            return Resource.empty();
        } else {
            return this.openTelemetry.getResource();
        }
    }

    /**
     * Used in io/jenkins/plugins/opentelemetry/JenkinsOpenTelemetryPluginConfiguration/config.jelly because
     * cyrille doesn't know how to format the content with linebreaks in a html teaxtarea
     */
    @NonNull
    public String getResourceAsText() {
        return this.getResource().getAttributes().asMap().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\r\n"));
    }

    /**
     * Returns active OpenTelemetry SDK configuration properties.
     *
     * @return SDK config properties, or empty defaults when unavailable
     */
    @NonNull
    public ConfigProperties getConfigProperties() {
        if (this.openTelemetry == null) {
            return DefaultConfigProperties.createFromMap(Collections.emptyMap());
        } else {
            return this.openTelemetry.getConfig();
        }
    }

    /**
     * Used in io/jenkins/plugins/opentelemetry/JenkinsOpenTelemetryPluginConfiguration/config.jelly because
     * cyrille doesn't know how to format the content with linebreaks in a html teaxtarea
     */
    @NonNull
    public String getNoteworthyConfigPropertiesAsText() {
        return OtelUtils.noteworthyConfigProperties(getConfigProperties()).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\r\n"));
    }

    /**
     * Returns currently resolved log storage retriever.
     *
     * @return log storage retriever
     * @throws IllegalStateException when retriever is not initialized
     */
    @NonNull
    public LogStorageRetriever getLogStorageRetriever() {
        if (logStorageRetriever == null) {
            throw new IllegalStateException("logStorageRetriever NOT loaded");
        }
        return logStorageRetriever;
    }

    @MustBeClosed
    @SuppressWarnings("MustBeClosedChecker")
    // false positive invoking backend.getLogStorageRetriever(templateBindingsProvider)
    private LogStorageRetriever resolveLogStorageRetriever() {
        LogStorageRetriever logStorageRetriever = null;
        if (JenkinsControllerOpenTelemetry.get().isLogsEnabled()) {
            Resource otelSdkResource = openTelemetry.getResource();
            String serviceName = Objects.requireNonNull(
                    otelSdkResource.getAttribute(ServiceAttributes.SERVICE_NAME), "service.name can't be null");
            String serviceNamespace = otelSdkResource.getAttribute(ServiceIncubatingAttributes.SERVICE_NAMESPACE);

            Map<String, Object> bindings;
            if (serviceNamespace == null) {
                bindings = Map.of(
                        ObservabilityBackend.TemplateBindings.SERVICE_NAME, serviceName,
                        ObservabilityBackend.TemplateBindings.SERVICE_NAMESPACE_AND_NAME, serviceName);
            } else {
                String serviceNamespaceAndName = serviceNamespace + "/" + serviceName;
                bindings = Map.of(
                        ObservabilityBackend.TemplateBindings.SERVICE_NAME, serviceName,
                        ObservabilityBackend.TemplateBindings.SERVICE_NAMESPACE, serviceNamespace,
                        ObservabilityBackend.TemplateBindings.SERVICE_NAMESPACE_AND_NAME, serviceNamespaceAndName);
            }

            for (ObservabilityBackend backend : getObservabilityBackends()) {
                logStorageRetriever =
                        backend.newLogStorageRetriever(TemplateBindingsProvider.compose(backend, bindings));
                if (logStorageRetriever != null) {
                    break;
                }
            }
            if (logStorageRetriever == null) {
                // "No observability backend configured to display the build logs for build with traceId: ${traceId}.
                // See
                // documentation: ",
                try {
                    logStorageRetriever = new CustomLogStorageRetriever(
                            new GStringTemplateEngine()
                                    .createTemplate(
                                            "https://plugins.jenkins.io/opentelemetry/"), // TODO better documentation
                            TemplateBindingsProvider.of(
                                    ObservabilityBackend.TemplateBindings.BACKEND_NAME,
                                            "See documentation on missing logs visualization URL",
                                    ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL,
                                            "/plugin/opentelemetry/svgs/opentelemetry.svg"));
                } catch (ClassNotFoundException | IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "resolveStorageRetriever: " + logStorageRetriever);
            }
        } else {
            LOGGER.log(Level.INFO, "Logs exporter is set to 'none', no log storage retriever configured");
        }
        return logStorageRetriever;
    }

    /**
     * https://github.com/spotbugs/spotbugs/issues/1175
     */
    @NonNull
    public static JenkinsOpenTelemetryPluginConfiguration get() {
        return Objects.requireNonNull(GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class));
    }

    /**
     * See <a href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/exporter.md#configuration-options">OpenTelemetry Specification / OpenTelemetry Protocol Exporter</a>
     * <p>
     * Target URL to which the exporter is going to send spans or metrics. The endpoint MUST be a valid URL with scheme
     * (http or https) and host, MAY contain a port, SHOULD contain a path and MUST NOT contain other parts
     * (such as query string or fragment).
     * A scheme of https indicates a secure connection.
     * </p>
     */
    public FormValidation doCheckEndpoint(@QueryParameter String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return FormValidation.ok();
        }
        URI endpointAsUrl;
        try {
            endpointAsUrl = new URI(endpoint);
        } catch (URISyntaxException e) {
            return FormValidation.error("Invalid URL: " + e.getMessage() + ".");
        }
        if (!"http".equals(endpointAsUrl.getScheme()) && !"https".equals(endpointAsUrl.getScheme())) {
            return FormValidation.error(
                    "Unsupported protocol '" + endpointAsUrl.getScheme() + "'. Expect 'https' or 'http' protocol.");
        }
        List<String> localhosts = Arrays.asList("localhost", "127.0.0.1", "0:0:0:0:0:0:0:1");
        for (String localhost : localhosts) {
            if (localhost.equals(endpointAsUrl.getHost())) {
                return FormValidation.warning(
                        "The OTLP Endpoint URL is also used from the Jenkins agents when sending logs through OTLP. "
                                + "Identifying the OTLP endpoint with the `" + localhost
                                + "` hostname is likely to not work from Jenkins agents.");
            }
        }
        return FormValidation.ok();
    }

    /**
     * Validates the period duration input.
     *
     * @param ignoredSteps the comma-separated list of steps to ignore.
     * @return ok if the form input was valid
     */
    public FormValidation doCheckIgnoredSteps(@QueryParameter String ignoredSteps) {
        if (ignoredSteps.matches("[A-Za-z0-9,]*")) {
            return FormValidation.ok();
        }
        return FormValidation.error("Invalid format: \"%s\".", ignoredSteps);
    }

    /**
     * A warning if it's selected.
     *
     * @param value the exportOtelConfigurationAsEnvironmentVariables flag
     * @return ok if the form input was valid
     */
    public FormValidation doCheckExportOtelConfigurationAsEnvironmentVariables(@QueryParameter String value) {
        if (value.equals("false")) {
            return FormValidation.ok();
        }
        return FormValidation.warning(
                "Note that OpenTelemetry credentials, if configured, will be exposed as environment variables (likely in OTEL_EXPORTER_OTLP_HEADERS)");
    }

    /**
     * Close the @link LogStorageRetriever}.
     * As <code>@PreDestroy</code> doesn't seem to be honored by Jenkins, we use <code>@Terminator</code> in addition.
     */
    @Terminator
    @PreDestroy
    public void preDestroy() throws Exception {
        if (logStorageRetriever != null) {
            LOGGER.log(Level.FINE, () -> "Close " + logStorageRetriever + "...");
            logStorageRetriever.close();
        }
    }

    /**
     * Immutable metadata for the Jenkins plugin that contributes a pipeline step.
     */
    @Immutable
    public static class StepPlugin {
        final String name;
        final String version;

        /**
         * Creates step plugin metadata.
         *
         * @param name plugin short name
         * @param version plugin version
         */
        public StepPlugin(String name, String version) {
            this.name = name;
            this.version = version;
        }

        /**
         * Creates an unknown step plugin placeholder.
         */
        public StepPlugin() {
            this.name = UNKNOWN;
            this.version = UNKNOWN;
        }

        /**
         * Returns plugin short name.
         *
         * @return plugin short name
         */
        public String getName() {
            return name;
        }

        /**
         * Returns plugin version.
         *
         * @return plugin version
         */
        public String getVersion() {
            return version;
        }

        /**
         * Returns whether metadata is unresolved.
         *
         * @return {@code true} when both name and version are unknown
         */
        public boolean isUnknown() {
            return getName().equals(UNKNOWN) && getVersion().equals(UNKNOWN);
        }

        /**
         * Returns readable representation of step plugin metadata.
         *
         * @return printable step plugin metadata
         */
        @Override
        public String toString() {
            return "StepPlugin{" + "name=" + name + ", version=" + version + '}';
        }
    }
}
