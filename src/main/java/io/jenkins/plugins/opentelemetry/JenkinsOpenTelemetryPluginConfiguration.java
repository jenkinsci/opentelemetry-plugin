/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import hudson.Extension;
import hudson.PluginWrapper;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.authentication.NoAuthentication;
import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.job.SpanNamingStrategy;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryEnvironmentVariablesConventions;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.CoreStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

    private int exporterTimeoutMillis = 30_000;

    private int exporterIntervalMillis = 60_000;

    private String ignoredSteps = "dir,echo,isUnix,pwd,properties";

    private transient OpenTelemetrySdkProvider openTelemetrySdkProvider;

    private boolean exportOtelConfigurationAsEnvironmentVariables;

    private transient SpanNamingStrategy spanNamingStrategy;

    private transient ConcurrentMap<String, StepPlugin> loadedStepsPlugins = new ConcurrentHashMap<>();

    private String serviceName;

    private String serviceNamespace;

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
        // stapler oddity, empty lists coming from the HTTP request are not set on bean by  "req.bindJSON(this, json)"
        this.observabilityBackends = req.bindJSONToList(ObservabilityBackend.class, json.get("observabilityBackends"));
        this.endpoint = sanitizeOtlpEndpoint(this.endpoint);
        initializeOpenTelemetry();
        save();
        return true;
    }

    @PostConstruct
    public void initializeOpenTelemetry() {
        OpenTelemetryConfiguration newOpenTelemetryConfiguration = new OpenTelemetryConfiguration(this.getEndpoint(), this.getTrustedCertificatesPem(), this.getAuthentication(), this.getExporterTimeoutMillis(), this.getExporterIntervalMillis(), this.getIgnoredSteps(), this.getServiceName(), this.getServiceNamespace());
        if (Objects.equal(this.currentOpenTelemetryConfiguration, newOpenTelemetryConfiguration)) {
            LOGGER.log(Level.FINE, "Configuration didn't change, skip reconfiguration");
            return;
        }
        openTelemetrySdkProvider.initialize(newOpenTelemetryConfiguration);
        this.currentOpenTelemetryConfiguration = newOpenTelemetryConfiguration;
    }

    /**
     *
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

    public int getExporterTimeoutMillis() {
        return exporterTimeoutMillis;
    }

    @DataBoundSetter
    public void setExporterTimeoutMillis(int exporterTimeoutMillis) {
        this.exporterTimeoutMillis = exporterTimeoutMillis;
        initializeOpenTelemetry();
    }

    public int getExporterIntervalMillis() {
        return exporterIntervalMillis;
    }

    @DataBoundSetter
    public void setExporterIntervalMillis(int exporterIntervalMillis) {
        this.exporterIntervalMillis = exporterIntervalMillis;
        initializeOpenTelemetry();
    }

    public String getIgnoredSteps() {
        return ignoredSteps;
    }

    public void setIgnoredSteps(String ignoredSteps) {
        this.ignoredSteps = ignoredSteps;
    }

    public boolean isExportOtelConfigurationAsEnvironmentVariables() {
        return exportOtelConfigurationAsEnvironmentVariables;
    }

    @DataBoundSetter
    public void setExportOtelConfigurationAsEnvironmentVariables(boolean exportOtelConfigurationAsEnvironmentVariables) {
        this.exportOtelConfigurationAsEnvironmentVariables = exportOtelConfigurationAsEnvironmentVariables;
    }

    @Nonnull
    public Map<String, String> getOtelConfigurationAsEnvironmentVariables() {
        Map<String, String> environmentVariables = new HashMap<>();
        environmentVariables.put(OpenTelemetryEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_ENDPOINT, this.endpoint);
        String sanitizeOtlpEndpoint = sanitizeOtlpEndpoint(this.endpoint);
        if (sanitizeOtlpEndpoint != null && sanitizeOtlpEndpoint.startsWith("http://")) {
            environmentVariables.put(OpenTelemetryEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_INSECURE, Boolean.TRUE.toString());
        }
        this.authentication.enrichOtelEnvironmentVariables(environmentVariables);
        String trustedCertificatesPem = this.getTrustedCertificatesPem();
        if (trustedCertificatesPem != null && !trustedCertificatesPem.isEmpty()) {
            environmentVariables.put(OpenTelemetryEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_CERTIFICATE, trustedCertificatesPem);
        }
        if (this.exporterTimeoutMillis != 0) {
            environmentVariables.put(OpenTelemetryEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_TIMEOUT, Integer.toString(this.exporterTimeoutMillis));
        }
        return environmentVariables;
    }

    /**
     * For visualisation in config.jelly
     */
    @Nonnull
    public String getVisualisationObservabilityBackendsString(){
        return "Visualisation observability backends: " + ObservabilityBackend.allDescriptors().stream().sorted().map(d-> d.getDisplayName()).collect(Collectors.joining(", "));
    }

    @Inject
    public void setSpanNamingStrategy(SpanNamingStrategy spanNamingStrategy) {
        this.spanNamingStrategy = spanNamingStrategy;
    }

    public SpanNamingStrategy getSpanNamingStrategy() {
        return spanNamingStrategy;
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
        if (data!=null) {
            LOGGER.log(Level.FINEST, " found the plugin for the step '" + stepName + "' - " + data);
            return data;
        }

        data = new StepPlugin();
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins!=null && descriptor!=null) {
            PluginWrapper wrapper = jenkins.getPluginManager().whichPlugin(descriptor.clazz);
            if (wrapper!=null) {
                data = new StepPlugin(wrapper.getShortName(), wrapper.getVersion());
                addStepPlugin(stepName, data);
            }
        }
        return data;
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

    public static JenkinsOpenTelemetryPluginConfiguration get() {
        return GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class);
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
        return FormValidation.error("Invalid format: \"%s\"", ignoredSteps);
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
            this.name = "unknown";
            this.version = "unknown";
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
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
