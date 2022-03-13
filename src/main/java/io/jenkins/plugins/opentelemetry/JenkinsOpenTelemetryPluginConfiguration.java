/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.base.Strings;
import groovy.text.GStringTemplateEngine;
import hudson.Extension;
import hudson.PluginWrapper;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.tasks.BuildStep;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.authentication.NoAuthentication;
import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.backend.custom.CustomLogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure.ConfigPropertiesUtils;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.OTelEnvironmentVariablesConventions;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import jenkins.model.CauseOfInterruption;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import net.sf.json.JSONObject;
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
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static io.jenkins.plugins.opentelemetry.OtelUtils.UNKNOWN;

@Extension
@Symbol("openTelemetry")
public class JenkinsOpenTelemetryPluginConfiguration extends GlobalConfiguration {
    private final static Logger LOGGER = Logger.getLogger(JenkinsOpenTelemetryPluginConfiguration.class.getName());

    /**
     * OTLP endpoint prefixed by "http://" or "https://"
     */
    private String endpoint;

    private String trustedCertificatesPem;

    private OtlpAuthentication authentication;

    private List<ObservabilityBackend> observabilityBackends = new ArrayList<>();

    private Integer exporterTimeoutMillis = null;

    private Integer exporterIntervalMillis = null;

    private String ignoredSteps = "dir,echo,isUnix,pwd,properties";

    private String disabledResourceProviders = OpenTelemetrySdkProvider.DEFAULT_OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS;

    private transient OpenTelemetrySdkProvider openTelemetrySdkProvider;

    private transient LogStorageRetriever logStorageRetriever;

    private boolean exportOtelConfigurationAsEnvironmentVariables;

    private transient ConcurrentMap<String, StepPlugin> loadedStepsPlugins = new ConcurrentHashMap<>();

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
    private List<String> statusUnsetCausesOfInterruption = Arrays.asList(
        "org.jenkinsci.plugins.workflow.cps.steps.ParallelStep$FailFastCause",
        StageStepExecution.CanceledCause.class.getName(),
        CauseOfInterruption.UserInterruption.class.getName()
    );

    /**
     * The previously used configuration. Kept in memory to prevent unneeded reconfigurations.
     */
    protected transient OpenTelemetryConfiguration currentOpenTelemetryConfiguration;

    @DataBoundConstructor
    public JenkinsOpenTelemetryPluginConfiguration() {
        load();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        // stapler oddity, empty lists coming from the HTTP request are not set on bean by  `req.bindJSON(this, json)`
        this.observabilityBackends = req.bindJSONToList(ObservabilityBackend.class, json.get("observabilityBackends"));
        this.endpoint = sanitizeOtlpEndpoint(this.endpoint);
        initializeOpenTelemetry();
        if (logStorageRetriever != null && logStorageRetriever instanceof Closeable) {
            LOGGER.log(Level.FINE, () -> "Close " + logStorageRetriever + "...");
            try {
                ((Closeable) logStorageRetriever).close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Exception closing currently setup logStorageRetriever: " + logStorageRetriever, e);
            }
        }
        this.logStorageRetriever = resolveLogStorageRetriever();
        save();
        return true;
    }

    protected Object readResolve() {
        if (this.disabledResourceProviders == null) {
            this.disabledResourceProviders = OpenTelemetrySdkProvider.DEFAULT_OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS;
        }
        this.logStorageRetriever = resolveLogStorageRetriever();
        return this;
    }


    @Nonnull
    public OpenTelemetryConfiguration toOpenTelemetryConfiguration() {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(Objects.toString(this.configurationProperties)));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Exception parsing configuration properties", e);
        }

        Map<String, String> configurationProperties = new HashMap();
        getObservabilityBackends().forEach(backend -> configurationProperties.putAll(backend.getOtelConfigurationProperties()));
        configurationProperties.put(JenkinsOtelSemanticAttributes.JENKINS_VERSION.getKey(), OtelUtils.getJenkinsVersion());
        configurationProperties.put(JenkinsOtelSemanticAttributes.JENKINS_URL.getKey(), this.jenkinsLocationConfiguration.getUrl());
        properties.forEach((k, v) -> configurationProperties.put(Objects.toString(k, "#null#"), Objects.toString(v, "#null#")));

        return new OpenTelemetryConfiguration(
            Optional.ofNullable(this.getEndpoint()),
            Optional.ofNullable(this.getTrustedCertificatesPem()),
            Optional.ofNullable(this.getAuthentication()),
            Optional.ofNullable(this.getExporterTimeoutMillis()),
            Optional.ofNullable(this.getExporterIntervalMillis()),
            Optional.ofNullable(this.getServiceName()),
            Optional.ofNullable(this.getServiceNamespace()),
            Optional.ofNullable(this.getDisabledResourceProviders()),
            configurationProperties);
    }

    @PostConstruct
    public void initializeOpenTelemetry() {
        OpenTelemetryConfiguration newOpenTelemetryConfiguration = toOpenTelemetryConfiguration();
        if (Objects.equals(this.currentOpenTelemetryConfiguration, newOpenTelemetryConfiguration)) {
            LOGGER.log(Level.FINE, "Configuration didn't change, skip reconfiguration");
            return;
        }
        openTelemetrySdkProvider.initialize(newOpenTelemetryConfiguration);
        this.currentOpenTelemetryConfiguration = newOpenTelemetryConfiguration;
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

    @DataBoundSetter
    public void setEndpoint(String endpoint) {
        this.endpoint = sanitizeOtlpEndpoint(endpoint);
        initializeOpenTelemetry();
    }

    @Nonnull
    public OtlpAuthentication getAuthentication() {
        return this.authentication == null ? new NoAuthentication() : this.authentication;
    }

    @DataBoundSetter
    public void setAuthentication(OtlpAuthentication authentication) {
        this.authentication = authentication;
        initializeOpenTelemetry();
    }

    @CheckForNull
    public String getTrustedCertificatesPem() {
        return trustedCertificatesPem;
    }

    @DataBoundSetter
    public void setTrustedCertificatesPem(String trustedCertificatesPem) {
        this.trustedCertificatesPem = trustedCertificatesPem;
        initializeOpenTelemetry();
    }

    @DataBoundSetter
    public void setObservabilityBackends(List<ObservabilityBackend> observabilityBackends) {
        this.observabilityBackends = Optional.of(observabilityBackends).orElse(Collections.emptyList());
        initializeOpenTelemetry();
    }

    @Nonnull
    public List<ObservabilityBackend> getObservabilityBackends() {
        if (observabilityBackends == null) {
            observabilityBackends = new ArrayList<>();
        }
        return observabilityBackends;
    }

    @Inject
    public void setOpenTelemetrySdkProvider(OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.openTelemetrySdkProvider = openTelemetrySdkProvider;
    }

    public Integer getExporterTimeoutMillis() {
        return exporterTimeoutMillis;
    }

    @DataBoundSetter
    public void setExporterTimeoutMillis(Integer exporterTimeoutMillis) {
        this.exporterTimeoutMillis = exporterTimeoutMillis;
        initializeOpenTelemetry();
    }

    public Integer getExporterIntervalMillis() {
        return exporterIntervalMillis;
    }

    @DataBoundSetter
    public void setExporterIntervalMillis(Integer exporterIntervalMillis) {
        this.exporterIntervalMillis = exporterIntervalMillis;
        initializeOpenTelemetry();
    }

    public String getIgnoredSteps() {
        return ignoredSteps;
    }

    @DataBoundSetter
    public void setIgnoredSteps(String ignoredSteps) {
        this.ignoredSteps = ignoredSteps;
    }

    public List<String> getStatusUnsetCausesOfInterruption() {
        return statusUnsetCausesOfInterruption;
    }

    public String getDisabledResourceProviders() {
        return disabledResourceProviders;
    }

    @DataBoundSetter
    public void setDisabledResourceProviders(String disabledResourceProviders) {
        this.disabledResourceProviders = disabledResourceProviders;
        initializeOpenTelemetry();
    }

    public boolean isExportOtelConfigurationAsEnvironmentVariables() {
        return exportOtelConfigurationAsEnvironmentVariables;
    }

    @DataBoundSetter
    public void setExportOtelConfigurationAsEnvironmentVariables(boolean exportOtelConfigurationAsEnvironmentVariables) {
        this.exportOtelConfigurationAsEnvironmentVariables = exportOtelConfigurationAsEnvironmentVariables;
    }

    public String getConfigurationProperties() {
        return configurationProperties;
    }

    @DataBoundSetter
    public void setConfigurationProperties(String configurationProperties) {
        this.configurationProperties = configurationProperties;
        initializeOpenTelemetry();
    }

    @Nonnull
    public Map<String, String> getOtelConfigurationAsEnvironmentVariables() {
        if (this.endpoint == null) {
            return Collections.emptyMap();
        }

        Map<String, String> environmentVariables = new HashMap<>();
        environmentVariables.put(OTelEnvironmentVariablesConventions.OTEL_TRACES_EXPORTER, "otlp");
        environmentVariables.put(OTelEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_ENDPOINT, this.endpoint);
        String sanitizeOtlpEndpoint = sanitizeOtlpEndpoint(this.endpoint);
        if (sanitizeOtlpEndpoint != null && sanitizeOtlpEndpoint.startsWith("http://")) {
            environmentVariables.put(OTelEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_INSECURE, Boolean.TRUE.toString());
        }
        this.authentication.enrichOtelEnvironmentVariables(environmentVariables);
        String trustedCertificatesPem = this.getTrustedCertificatesPem();
        if (trustedCertificatesPem != null && !trustedCertificatesPem.isEmpty()) {
            environmentVariables.put(OTelEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_CERTIFICATE, trustedCertificatesPem);
        }
        if (this.exporterTimeoutMillis != null) {
            environmentVariables.put(OTelEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_TIMEOUT, Integer.toString(this.exporterTimeoutMillis));
        }
        return environmentVariables;
    }

    private JenkinsLocationConfiguration jenkinsLocationConfiguration;

    @Inject
    public void setJenkinsLocationConfiguration(@Nonnull JenkinsLocationConfiguration jenkinsLocationConfiguration) {
        this.jenkinsLocationConfiguration = jenkinsLocationConfiguration;
    }

    /**
     * For visualisation in config.jelly
     */
    @Nonnull
    public String getVisualisationObservabilityBackendsString() {
        return "Visualisation observability backends: " + ObservabilityBackend.allDescriptors().stream().sorted().map(d -> d.getDisplayName()).collect(Collectors.joining(", "));
    }

    @Nonnull
    public ConcurrentMap<String, StepPlugin> getLoadedStepsPlugins() {
        return loadedStepsPlugins;
    }

    public void addStepPlugin(String stepName, StepPlugin c) {
        loadedStepsPlugins.put(stepName, c);
    }

    @Nullable
    private Descriptor<? extends Describable> getStepDescriptor(@Nonnull FlowNode node, @Nullable Descriptor<? extends Describable> descriptor) {
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

    @Nullable
    private Descriptor<? extends Describable> getBuildStepDescriptor(@Nonnull BuildStep buildStep) {
        return Jenkins.get().getDescriptor((Class<? extends Describable>) buildStep.getClass());
    }

    @Nonnull
    public StepPlugin findStepPluginOrDefault(@Nonnull String buildStepName, @Nonnull BuildStep buildStep) {
        return findStepPluginOrDefault(buildStepName, getBuildStepDescriptor(buildStep));
    }

    @Nonnull
    public StepPlugin findStepPluginOrDefault(@Nonnull String stepName, @Nonnull StepAtomNode node) {
        return findStepPluginOrDefault(stepName, getStepDescriptor(node, node.getDescriptor()));
    }

    @Nonnull
    public StepPlugin findStepPluginOrDefault(@Nonnull String stepName, @Nonnull StepStartNode node) {
        return findStepPluginOrDefault(stepName, getStepDescriptor(node, node.getDescriptor()));
    }

    @Nonnull
    public StepPlugin findStepPluginOrDefault(@Nonnull String stepName, @Nullable Descriptor<? extends Describable> descriptor) {
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

    @Nonnull
    public String findSymbolOrDefault(@Nonnull String buildStepName, @Nonnull BuildStep buildStep) {
        return findSymbolOrDefault(buildStepName, getBuildStepDescriptor(buildStep));
    }

    @Nonnull
    public String findSymbolOrDefault(@Nonnull String buildStepName, @Nullable Descriptor<? extends Describable> descriptor) {
        String value = buildStepName;
        if (descriptor != null) {
            Set<String> values = SymbolLookup.getSymbolValue(descriptor);
            value = values.stream().findFirst().orElse(buildStepName);
        }
        return value;
    }

    /**
     * @see io.opentelemetry.semconv.resource.attributes.ResourceAttributes#SERVICE_NAME
     */
    public String getServiceName() {
        return (Strings.isNullOrEmpty(this.serviceName)) ? JenkinsOtelSemanticAttributes.JENKINS : this.serviceName;
    }

    @DataBoundSetter
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
        initializeOpenTelemetry();
    }

    /**
     * @see io.opentelemetry.semconv.resource.attributes.ResourceAttributes#SERVICE_NAMESPACE
     */
    public String getServiceNamespace() {
        return (Strings.isNullOrEmpty(this.serviceNamespace)) ? JenkinsOtelSemanticAttributes.JENKINS : this.serviceNamespace;
    }

    @DataBoundSetter
    public void setServiceNamespace(String serviceNamespace) {
        this.serviceNamespace = serviceNamespace;
        initializeOpenTelemetry();
    }

    @Nonnull
    public Resource getResource() {
        if (this.openTelemetrySdkProvider == null) {
            return Resource.empty();
        } else {
            return this.openTelemetrySdkProvider.getResource();
        }
    }

    /**
     * Used in io/jenkins/plugins/opentelemetry/JenkinsOpenTelemetryPluginConfiguration/config.jelly because
     * cyrille doesn't know how to format the content with linebreaks in a html teaxtarea
     */
    @Nonnull
    public String getResourceAsText() {
        return this.getResource().getAttributes().asMap().entrySet().stream().
            map(e -> e.getKey() + "=" + e.getValue()).
            collect(Collectors.joining("\r\n"));
    }

    @Nonnull
    public ConfigProperties getConfigProperties() {
        if (this.openTelemetrySdkProvider == null) {
            return ConfigPropertiesUtils.emptyConfig();
        } else {
            return this.openTelemetrySdkProvider.getConfig();
        }
    }

    /**
     * Used in io/jenkins/plugins/opentelemetry/JenkinsOpenTelemetryPluginConfiguration/config.jelly because
     * cyrille doesn't know how to format the content with linebreaks in a html teaxtarea
     */
    @Nonnull
    public String getNoteworthyConfigPropertiesAsText() {
        return OtelUtils.noteworthyConfigProperties(getConfigProperties()).entrySet().stream().
            map(e -> e.getKey() + "=" + e.getValue()).
            collect(Collectors.joining("\r\n"));
    }

    @Nonnull
    public LogStorageRetriever getLogStorageRetriever() {
        if (logStorageRetriever == null) {
            throw new IllegalStateException("logStorageRetriever NOT loaded");
        }
        return logStorageRetriever;
    }

    @Nonnull
    private LogStorageRetriever resolveLogStorageRetriever() {
        LogStorageRetriever logStorageRetriever = null;
        for (ObservabilityBackend backend : getObservabilityBackends()) {
            ObservabilityBackend templateBindingsProvider = backend; // TODO make JenkinsOpenTelemetryPluginConfiguration a templateBindingsProvider
            logStorageRetriever = backend.getLogStorageRetriever(templateBindingsProvider);
            if (logStorageRetriever != null) {
                break;
            }
        }
        if (logStorageRetriever == null) {
            // "No observability backend configured to display the build logs for build with traceId: ${traceId}. See documentation: ",
            try {
                logStorageRetriever = new CustomLogStorageRetriever(
                    new GStringTemplateEngine().createTemplate("https://plugins.jenkins.io/opentelemetry/"), // TODO better documentation URL
                    TemplateBindingsProvider.of(
                        ObservabilityBackend.TemplateBindings.BACKEND_NAME, "See documentation on missing logs visualization URL",
                        ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL, "/plugin/opentelemetry/images/24x24/opentelemetry.png"));
            } catch (ClassNotFoundException | IOException e) {
                throw new IllegalStateException(e);
            }
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "resolveStorageRetriever: " + logStorageRetriever);
        }
        return logStorageRetriever;
    }

    public static JenkinsOpenTelemetryPluginConfiguration get() {
        return GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class);
    }

    /**
     * See <a href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/exporter.md#configuration-options">OpenTelemetry Specification / OpenTelemetry Protocol Exporter</a>
     * <p>
     * Target URL to which the exporter is going to send spans or metrics. The endpoint MUST be a valid URL with scheme
     * (http or https) and host, MAY contain a port, SHOULD contain a path and MUST NOT contain other parts
     * (such as query string or fragment).
     * A scheme of https indicates a secure connection.
     * </p>
     *
     * @param endpoint
     * @return
     */
    public FormValidation doCheckEndpoint(@QueryParameter String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return FormValidation.ok();
        }
        URL endpointAsUrl;
        try {
            endpointAsUrl = new URL(endpoint);
        } catch (MalformedURLException e) {
            return FormValidation.error("Invalid URL: " + e.getMessage() + ".");
        }
        if (!"http".equals(endpointAsUrl.getProtocol()) && !"https".equals(endpointAsUrl.getProtocol())) {
            return FormValidation.error("Unsupported protocol '" + endpointAsUrl.getProtocol() + "'. Expect 'https' or 'http' protocol.");
        }
        List<String> localhosts = Arrays.asList("localhost", "127.0.0.1", "0:0:0:0:0:0:0:1");
        for (String localhost : localhosts) {
            if (localhost.equals(endpointAsUrl.getHost())) {
                return FormValidation.warning("The OTLP Endpoint URL is also used from the Jenkins agents when sending logs through OTLP. " +
                    "Identifying the OTLP endpoint with the `" + localhost + "` hostname is likely to not work from Jenkins agents.");
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
        return FormValidation.warning("Note that OpenTelemetry credentials, if configured, will be exposed as environment variables (likely in OTEL_EXPORTER_OTLP_HEADERS)");
    }

    @PreDestroy
    public void preDestroy() throws IOException {
        if (logStorageRetriever != null && logStorageRetriever instanceof Closeable) {
            LOGGER.log(Level.FINE, () -> "Close " + logStorageRetriever + "...");
            ((Closeable) logStorageRetriever).close();
        }
    }

    @Immutable
    public static class StepPlugin {
        final String name;
        final String version;

        public StepPlugin(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public StepPlugin() {
            this.name = UNKNOWN;
            this.version = UNKNOWN;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public boolean isUnknown() {
            return getName().equals(UNKNOWN) && getVersion().equals(UNKNOWN);
        }

        @Override
        public String toString() {
            return "StepPlugin{" +
                "name=" + name +
                ", version=" + version +
                '}';
        }
    }
}
