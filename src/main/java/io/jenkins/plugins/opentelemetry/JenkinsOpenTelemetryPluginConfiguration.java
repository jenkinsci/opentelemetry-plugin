/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.base.Strings;
import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.text.GStringTemplateEngine;
import hudson.Extension;
import hudson.PluginWrapper;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
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
import io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure.ConfigPropertiesUtils;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.OTelEnvironmentVariablesConventions;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
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
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
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

import static io.jenkins.plugins.opentelemetry.OtelUtils.UNKNOWN;
import static io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend.ICONS_PREFIX;

@Extension(ordinal = Integer.MAX_VALUE-1 /* initialize OTel ASAP, just after loading JenkinsControllerOpenTelemetry as GlobalOpenTelemetry */)
@Symbol("openTelemetry")
public class JenkinsOpenTelemetryPluginConfiguration extends GlobalConfiguration {
    private final static Logger LOGGER = Logger.getLogger(JenkinsOpenTelemetryPluginConfiguration.class.getName());

    static {
        IconSet.icons.addIcon(
            new Icon(
                "icon-otel icon-sm",
                ICONS_PREFIX + "opentelemetry.svg",
                Icon.ICON_SMALL_STYLE));
        IconSet.icons.addIcon(
            new Icon(
                "icon-otel icon-md",
                ICONS_PREFIX + "opentelemetry.svg",
                Icon.ICON_MEDIUM_STYLE));
        IconSet.icons.addIcon(
            new Icon(
                "icon-otel icon-lg",
                ICONS_PREFIX + "opentelemetry.svg",
                Icon.ICON_LARGE_STYLE));
        IconSet.icons.addIcon(
            new Icon(
                "icon-otel icon-xlg",
                ICONS_PREFIX + "opentelemetry.svg",
                Icon.ICON_XLARGE_STYLE));
    }


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

    private String disabledResourceProviders = JenkinsControllerOpenTelemetry.DEFAULT_OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS;

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
        LOGGER.log(Level.FINE, "Configure...");
        req.bindJSON(this, json);
        // stapler oddity, empty lists coming from the HTTP request are not set on bean by  `req.bindJSON(this, json)`
        this.observabilityBackends = req.bindJSONToList(ObservabilityBackend.class, json.get("observabilityBackends"));
        this.endpoint = sanitizeOtlpEndpoint(this.endpoint);
        initializeOpenTelemetry();
        save();
        LOGGER.log(Level.FINE, "Configured");
        return true;
    }

    protected Object readResolve() {
        LOGGER.log(Level.FINE, "readResolve()");
        if (this.disabledResourceProviders == null) {
            this.disabledResourceProviders = JenkinsControllerOpenTelemetry.DEFAULT_OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS;
        }
        return this;
    }

    @NonNull
    public OpenTelemetryConfiguration toOpenTelemetryConfiguration() {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(Objects.toString(this.configurationProperties)));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Exception parsing configuration properties", e);
        }

        Map<String, String> configurationProperties = new HashMap<>();
        getObservabilityBackends().forEach(backend -> configurationProperties.putAll(backend.getOtelConfigurationProperties()));
        configurationProperties.put(JenkinsOtelSemanticAttributes.JENKINS_VERSION.getKey(), OtelUtils.getJenkinsVersion());
        configurationProperties.put(JenkinsOtelSemanticAttributes.JENKINS_URL.getKey(), this.jenkinsLocationConfiguration.getUrl());
        // use same Jenkins instance identifier as the Jenkins Support Core plugin. No need to add the complexity of the instance-identity-plugin
        // https://github.com/jenkinsci/support-core-plugin/blob/support-core-2.81/src/main/java/com/cloudbees/jenkins/support/impl/AboutJenkins.java#L401
        configurationProperties.put(ServiceIncubatingAttributes.SERVICE_INSTANCE_ID.getKey(), Jenkins.get().getLegacyInstanceId());
        properties.forEach((k, v) -> configurationProperties.put(Objects.toString(k, "#null#"), Objects.toString(v, "#null#")));

        return new OpenTelemetryConfiguration(
            Optional.ofNullable(this.getEndpoint()),
            Optional.ofNullable(this.getTrustedCertificatesPem()),
            Optional.of(this.getAuthentication()),
            Optional.ofNullable(this.getExporterTimeoutMillis()),
            Optional.ofNullable(this.getExporterIntervalMillis()),
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
    public void initializeOpenTelemetry() {
        LOGGER.log(Level.FINE, "Initialize Jenkins OpenTelemetry Plugin...");
        OpenTelemetryConfiguration newOpenTelemetryConfiguration = toOpenTelemetryConfiguration();
        if (Objects.equals(this.currentOpenTelemetryConfiguration, newOpenTelemetryConfiguration)) {
            LOGGER.log(Level.FINE, "Configuration didn't change, skip reconfiguration");
        } else {
            openTelemetry.configure(newOpenTelemetryConfiguration.toOpenTelemetryProperties(), newOpenTelemetryConfiguration.toOpenTelemetryResource());
            this.currentOpenTelemetryConfiguration = newOpenTelemetryConfiguration;
        }

        if (logStorageRetriever != null && logStorageRetriever instanceof Closeable) {
            LOGGER.log(Level.FINE, () -> "Close " + logStorageRetriever + "...");
            try {
                ((Closeable) logStorageRetriever).close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Exception closing currently setup logStorageRetriever: " + logStorageRetriever, e);
            }
        }
        this.logStorageRetriever = resolveLogStorageRetriever();
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
        // debug line used to verify the lifecycle (@Initializer) when using JCasC configuration
        LOGGER.log(Level.FINE, () -> "setEndpoint(" + endpoint + ")");
    }

    @NonNull
    public OtlpAuthentication getAuthentication() {
        return this.authentication == null ? new NoAuthentication() : this.authentication;
    }

    @DataBoundSetter
    public void setAuthentication(OtlpAuthentication authentication) {
        this.authentication = authentication;
    }

    @CheckForNull
    public String getTrustedCertificatesPem() {
        return trustedCertificatesPem;
    }

    @DataBoundSetter
    public void setTrustedCertificatesPem(String trustedCertificatesPem) {
        this.trustedCertificatesPem = trustedCertificatesPem;
    }

    @DataBoundSetter
    public void setObservabilityBackends(List<ObservabilityBackend> observabilityBackends) {
        this.observabilityBackends = observabilityBackends == null ? Collections.emptyList() : observabilityBackends;
    }

    @NonNull
    public List<ObservabilityBackend> getObservabilityBackends() {
        if (observabilityBackends == null) {
            observabilityBackends = new ArrayList<>();
        }
        return observabilityBackends;
    }

    public Integer getExporterTimeoutMillis() {
        return exporterTimeoutMillis;
    }

    @DataBoundSetter
    public void setExporterTimeoutMillis(Integer exporterTimeoutMillis) {
        this.exporterTimeoutMillis = exporterTimeoutMillis;

    }

    public Integer getExporterIntervalMillis() {
        return exporterIntervalMillis;
    }

    @DataBoundSetter
    public void setExporterIntervalMillis(Integer exporterIntervalMillis) {
        this.exporterIntervalMillis = exporterIntervalMillis;

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

    }

    @NonNull
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
    public void setJenkinsLocationConfiguration(@NonNull JenkinsLocationConfiguration jenkinsLocationConfiguration) {
        this.jenkinsLocationConfiguration = jenkinsLocationConfiguration;
    }

    /**
     * For visualisation in config.jelly
     */
    @NonNull
    public String getVisualisationObservabilityBackendsString() {
        return "Visualisation observability backends: " + ObservabilityBackend.allDescriptors().stream().sorted().map(d -> d.getDisplayName()).collect(Collectors.joining(", "));
    }

    @NonNull
    public ConcurrentMap<String, StepPlugin> getLoadedStepsPlugins() {
        return loadedStepsPlugins;
    }

    public void addStepPlugin(String stepName, StepPlugin c) {
        loadedStepsPlugins.put(stepName, c);
    }

    @Nullable
    private Descriptor<? extends Describable> getStepDescriptor(@NonNull FlowNode node, @Nullable Descriptor<? extends Describable> descriptor) {
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
    private Descriptor<? extends Describable> getBuildStepDescriptor(@NonNull BuildStep buildStep) {
        return Jenkins.get().getDescriptor((Class<? extends Describable>) buildStep.getClass());
    }

    @NonNull
    public StepPlugin findStepPluginOrDefault(@NonNull String buildStepName, @NonNull BuildStep buildStep) {
        return findStepPluginOrDefault(buildStepName, getBuildStepDescriptor(buildStep));
    }

    @NonNull
    public StepPlugin findStepPluginOrDefault(@NonNull String stepName, @NonNull StepAtomNode node) {
        return findStepPluginOrDefault(stepName, getStepDescriptor(node, node.getDescriptor()));
    }

    @NonNull
    public StepPlugin findStepPluginOrDefault(@NonNull String stepName, @NonNull StepStartNode node) {
        return findStepPluginOrDefault(stepName, getStepDescriptor(node, node.getDescriptor()));
    }

    @NonNull
    public StepPlugin findStepPluginOrDefault(@NonNull String stepName, @Nullable Descriptor<? extends Describable> descriptor) {
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

    @NonNull
    public String findSymbolOrDefault(@NonNull String buildStepName, @NonNull BuildStep buildStep) {
        return findSymbolOrDefault(buildStepName, getBuildStepDescriptor(buildStep));
    }

    @NonNull
    public String findSymbolOrDefault(@NonNull String buildStepName, @Nullable Descriptor<? extends Describable> descriptor) {
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
        return (Strings.isNullOrEmpty(this.serviceName)) ? JenkinsOtelSemanticAttributes.JENKINS : this.serviceName;
    }

    @DataBoundSetter
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;

    }

    /**
     * @see io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes#SERVICE_NAMESPACE
     */
    public String getServiceNamespace() {
        return (Strings.isNullOrEmpty(this.serviceNamespace)) ? JenkinsOtelSemanticAttributes.JENKINS : this.serviceNamespace;
    }

    @DataBoundSetter
    public void setServiceNamespace(String serviceNamespace) {
        this.serviceNamespace = serviceNamespace;

    }

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
        return this.getResource().getAttributes().asMap().entrySet().stream().
            map(e -> e.getKey() + "=" + e.getValue()).
            collect(Collectors.joining("\r\n"));
    }

    @NonNull
    public ConfigProperties getConfigProperties() {
        if (this.openTelemetry == null) {
            return ConfigPropertiesUtils.emptyConfig();
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
        return OtelUtils.noteworthyConfigProperties(getConfigProperties()).entrySet().stream().
            map(e -> e.getKey() + "=" + e.getValue()).
            collect(Collectors.joining("\r\n"));
    }

    @NonNull
    public LogStorageRetriever getLogStorageRetriever() {
        if (logStorageRetriever == null) {
            throw new IllegalStateException("logStorageRetriever NOT loaded");
        }
        return logStorageRetriever;
    }

    @NonNull
    @MustBeClosed
    @SuppressWarnings("MustBeClosedChecker")
    // false positive invoking backend.getLogStorageRetriever(templateBindingsProvider)
    private LogStorageRetriever resolveLogStorageRetriever() {
        LogStorageRetriever logStorageRetriever = null;

        Resource otelSdkResource = openTelemetry.getResource();
        String serviceName = Objects.requireNonNull(otelSdkResource.getAttribute(ServiceAttributes.SERVICE_NAME), "service.name can't be null");
        String serviceNamespace = otelSdkResource.getAttribute(ServiceIncubatingAttributes.SERVICE_NAMESPACE);

        Map<String, Object> bindings;
        if (serviceNamespace == null) {
            bindings = Map.of(
                ObservabilityBackend.TemplateBindings.SERVICE_NAME, serviceName,
                ObservabilityBackend.TemplateBindings.SERVICE_NAMESPACE_AND_NAME, serviceName
            );
        } else {
            String serviceNamespaceAndName = serviceNamespace + "/" + serviceName;
            bindings = Map.of(
                ObservabilityBackend.TemplateBindings.SERVICE_NAME, serviceName,
                ObservabilityBackend.TemplateBindings.SERVICE_NAMESPACE, serviceNamespace,
                ObservabilityBackend.TemplateBindings.SERVICE_NAMESPACE_AND_NAME, serviceNamespaceAndName
            );
        }

        for (ObservabilityBackend backend : getObservabilityBackends()) {
            logStorageRetriever = backend.newLogStorageRetriever(TemplateBindingsProvider.compose(backend, bindings));
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
                        ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL, "/plugin/opentelemetry/svgs/opentelemetry.svg"));
            } catch (ClassNotFoundException | IOException e) {
                throw new IllegalStateException(e);
            }
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "resolveStorageRetriever: " + logStorageRetriever);
        }
        return logStorageRetriever;
    }

    /**
     * https://github.com/spotbugs/spotbugs/issues/1175
     */
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
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
    public void preDestroy() throws Exception {
        if (logStorageRetriever != null) {
            LOGGER.log(Level.FINE, () -> "Close " + logStorageRetriever + "...");
            logStorageRetriever.close();
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
