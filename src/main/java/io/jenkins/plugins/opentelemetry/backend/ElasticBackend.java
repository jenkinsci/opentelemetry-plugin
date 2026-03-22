/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticLogsBackend;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import jenkins.model.GlobalConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class ElasticBackend extends ObservabilityBackend {

    /** Environment variable name used to pass the Elastic APM server URL to the agent. */
    public static final String OTEL_ELASTIC_URL = "OTEL_ELASTIC_URL";
    /** Default display name for the Elastic Observability backend. */
    public static final String DEFAULT_BACKEND_NAME = "Elastic Observability";
    /** Default title for the Kibana Jenkins overview dashboard. */
    public static final String DEFAULT_KIBANA_DASHBOARD_TITLE = "Jenkins Overview";
    /** Default Kibana space identifier (empty string means the default space). */
    public static final String DEFAULT_KIBANA_SPACE_IDENTIFIER = "";
    /** Default URL query parameters appended to Kibana dashboard links. */
    public static final String DEFAULT_KIBANA_DASHBOARD_QUERY_PARAMETERS = "title=${kibanaDashboardTitle}&"
            + "_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-24h%2Fh,to:now))";

    static {
        IconSet.icons.addIcon(
                new Icon("icon-otel-elastic icon-sm", ICONS_PREFIX + "elastic.svg", Icon.ICON_SMALL_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel-elastic icon-md", ICONS_PREFIX + "elastic.svg", Icon.ICON_MEDIUM_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel-elastic icon-lg", ICONS_PREFIX + "elastic.svg", Icon.ICON_LARGE_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-otel-elastic icon-xlg", ICONS_PREFIX + "elastic.svg", Icon.ICON_XLARGE_STYLE));
    }

    private boolean displayKibanaDashboardLink;

    private String kibanaBaseUrl;

    /**
     * See https://www.elastic.co/guide/en/kibana/master/xpack-spaces.html
     */
    private String kibanaSpaceIdentifier;

    private String kibanaDashboardTitle;

    private String kibanaDashboardUrlParameters;

    private ElasticLogsBackend elasticLogsBackend;

    private boolean enableEDOT;

    /**
     * Creates Elastic backend configuration with defaults.
     */
    @DataBoundConstructor
    public ElasticBackend() {}

    /**
     * Merges provided template bindings with backend-specific bindings.
     *
     * @param bindings base bindings
     * @return merged bindings
     */
    @Override
    public Map<String, Object> mergeBindings(Map<String, Object> bindings) {
        Map<String, Object> mergedBindings = new HashMap<>(bindings);
        mergedBindings.putAll(getBindings());
        return mergedBindings;
    }

    /**
     * Returns template bindings used to render Elastic links.
     *
     * @return Elastic-specific bindings
     */
    @Override
    public Map<String, Object> getBindings() {
        Map<String, Object> bindings = new LinkedHashMap<>();
        bindings.put(TemplateBindings.BACKEND_NAME, getName());
        bindings.put(TemplateBindings.BACKEND_24_24_ICON_URL, "/plugin/opentelemetry/images/24x24/elastic.png");
        bindings.put(TemplateBindings.KIBANA_BASE_URL, this.getKibanaBaseUrl());
        bindings.put(TemplateBindings.KIBANA_DASHBOARD_TITLE, OtelUtils.urlEncode(this.kibanaDashboardTitle));
        bindings.put(TemplateBindings.KIBANA_SPACE_IDENTIFIER, OtelUtils.urlEncode(this.kibanaSpaceIdentifier));
        return bindings;
    }

    /**
     * Returns the URL template for opening a trace in the Elastic APM UI.
     *
     * @return trace visualization URL template, or {@code null} when Kibana is not configured
     */
    @CheckForNull
    @Override
    public String getTraceVisualisationUrlTemplate() {
        String transactionType = enableEDOT ? "unknown" : "job";
        return getEffectiveKibanaURL() + "/app/apm/services/${serviceName}/transactions/view"
                + "?rangeFrom=${startTime.minusSeconds(600)}"
                + "&rangeTo=${startTime.plusSeconds(600)}"
                + "&transactionName=${rootSpanName}"
                + "&transactionType="
                + transactionType
                + // see io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes.ELASTIC_TRANSACTION_TYPE
                "&comparisonEnabled=true"
                + "&transactionId=${spanId}"
                + "&traceId=${traceId}";
    }

    /**
     * Returns configured Kibana base URL with trailing slash removed.
     *
     * @return Kibana base URL, or {@code null} when unset
     */
    @CheckForNull
    public String getKibanaBaseUrl() {
        if (kibanaBaseUrl != null && kibanaBaseUrl.endsWith("/")) {
            kibanaBaseUrl = kibanaBaseUrl.substring(0, kibanaBaseUrl.length() - 1);
        }
        return kibanaBaseUrl;
    }

    /**
     * Sets Kibana base URL.
     *
     * @param kibanaBaseUrl Kibana base URL
     */
    @DataBoundSetter
    public void setKibanaBaseUrl(String kibanaBaseUrl) {
        this.kibanaBaseUrl = kibanaBaseUrl;
    }

    /**
     * Returns the icon path for this Elastic backend.
     *
     * @return icon identifier string
     */
    @CheckForNull
    @Override
    public String getIconPath() {
        return "icon-otel-elastic";
    }

    /**
     * Returns the environment variable name used to pass the Elastic URL to the agent.
     *
     * @return environment variable name
     */
    @CheckForNull
    @Override
    public String getEnvVariableName() {
        return OTEL_ELASTIC_URL;
    }

    /**
     * Returns the default display name for this backend.
     *
     * @return default backend name
     */
    @CheckForNull
    @Override
    public String getDefaultName() {
        return DEFAULT_BACKEND_NAME;
    }

    /**
     * Returns the URL template for opening the Kibana metrics dashboard, or {@code null}
     * when the dashboard link is disabled or no dashboard is configured.
     *
     * @return metrics dashboard URL template, or {@code null}
     */
    @CheckForNull
    @Override
    public String getMetricsVisualizationUrlTemplate() {
        if (!displayKibanaDashboardLink) {
            return null;
        }
        // see https://www.elastic.co/guide/en/kibana/6.8/sharing-dashboards.html
        String kibanaSpaceBaseUrl = getEffectiveKibanaURL() + "/app/kibana#/dashboards?";
        kibanaSpaceBaseUrl += this.getKibanaDashboardUrlParameters();
        return kibanaSpaceBaseUrl;
    }

    /**
     * Returns nested Elastic logs backend configuration.
     *
     * @return Elastic logs backend, or {@code null} when unset
     */
    public ElasticLogsBackend getElasticLogsBackend() {
        return elasticLogsBackend;
    }

    /**
     * Sets nested Elastic logs backend configuration.
     *
     * @param elasticLogsBackend Elastic logs backend
     */
    @DataBoundSetter
    public void setElasticLogsBackend(ElasticLogsBackend elasticLogsBackend) {
        this.elasticLogsBackend = elasticLogsBackend;
    }

    /**
     * Creates a log storage retriever that delegates to the configured Elastic logs backend.
     *
     * @param templateBindingsProvider provider for template bindings used to construct log query URLs
     * @return a log storage retriever, or {@code null} when no logs backend is configured
     */
    @Nullable
    @Override
    @MustBeClosed
    public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
        if (elasticLogsBackend == null) {
            return null;
        } else {
            return elasticLogsBackend.newLogStorageRetriever(templateBindingsProvider);
        }
    }

    /**
     * Returns OTel SDK configuration properties contributed by the Elastic logs backend.
     *
     * @return a map of OTel configuration properties, or an empty map when no logs backend is set
     */
    @NonNull
    @Override
    public Map<String, String> getOtelConfigurationProperties() {
        } else {
            return elasticLogsBackend.getOtelConfigurationProperties();
        }
    }

    /**
     * Returns Kibana space identifier.
     *
     * @return configured space identifier, or default when unset
     */
    @NonNull
    public String getKibanaSpaceIdentifier() {
        return Objects.toString(kibanaSpaceIdentifier, DEFAULT_KIBANA_SPACE_IDENTIFIER);
    }

    /**
     * Sets Kibana space identifier.
     *
     * @param kibanaSpaceIdentifier Kibana space identifier
     */
    @DataBoundSetter
    public void setKibanaSpaceIdentifier(String kibanaSpaceIdentifier) {
        this.kibanaSpaceIdentifier = kibanaSpaceIdentifier;
    }

    /**
     * Returns Kibana dashboard title.
     *
     * @return configured dashboard title, or default when unset
     */
    @NonNull
    public String getKibanaDashboardTitle() {
        return Objects.toString(kibanaDashboardTitle, DEFAULT_KIBANA_DASHBOARD_TITLE);
    }

    /**
     * Sets Kibana dashboard title.
     *
     * @param kibanaDashboardTitle dashboard title
     */
    @DataBoundSetter
    public void setKibanaDashboardTitle(String kibanaDashboardTitle) {
        this.kibanaDashboardTitle = kibanaDashboardTitle;
    }

    /**
     * Returns dashboard URL query parameters.
     *
     * @return configured query parameters, or defaults when unset
     */
    public String getKibanaDashboardUrlParameters() {
        return Objects.toString(kibanaDashboardUrlParameters, DEFAULT_KIBANA_DASHBOARD_QUERY_PARAMETERS);
    }

    /**
     * Sets dashboard URL query parameters.
     *
     * @param kibanaDashboardUrlParameters URL query parameters
     */
    @DataBoundSetter
    public void setKibanaDashboardUrlParameters(String kibanaDashboardUrlParameters) {
        this.kibanaDashboardUrlParameters = kibanaDashboardUrlParameters;
    }

    /**
     * Returns whether dashboard link should be shown in Jenkins UI.
     *
     * @return {@code true} when dashboard link is enabled
     */
    public boolean isDisplayKibanaDashboardLink() {
        return displayKibanaDashboardLink;
    }

    /**
     * Sets whether dashboard link should be shown in Jenkins UI.
     *
     * @param displayKibanaDashboardLink enable flag
     */
    @DataBoundSetter
    public void setDisplayKibanaDashboardLink(boolean displayKibanaDashboardLink) {
        this.displayKibanaDashboardLink = displayKibanaDashboardLink;
    }

    /**
     * Returns whether EDOT transaction type compatibility mode is enabled.
     *
     * @return {@code true} when EDOT mode is enabled
     */
    public boolean isEnableEDOT() {
        return enableEDOT;
    }

    /**
     * Sets whether EDOT transaction type compatibility mode is enabled.
     *
     * @param enableEDOT enable flag
     */
    @DataBoundSetter
    public void setEnableEDOT(boolean enableEDOT) {
        this.enableEDOT = enableEDOT;
    }

    /**
     * Returns the effective Kibana URL, including the space identifier if it is set.
     *
     * @return the effective Kibana URL
     */
    @NonNull
    public String getEffectiveKibanaURL() {
        String effectiveUrl = this.getKibanaBaseUrl();
        if (StringUtils.isNotBlank(this.getKibanaSpaceIdentifier())) {
            effectiveUrl += "/s/" + this.getKibanaSpaceIdentifier();
        }
        return effectiveUrl;
    }

    /**
     * Compares backend configuration values.
     *
     * @param o object to compare
     * @return {@code true} when configurations are equivalent
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElasticBackend that = (ElasticBackend) o;
        return displayKibanaDashboardLink == that.displayKibanaDashboardLink
                && Objects.equals(kibanaBaseUrl, that.kibanaBaseUrl)
                && Objects.equals(kibanaSpaceIdentifier, that.kibanaSpaceIdentifier)
                && Objects.equals(kibanaDashboardTitle, that.kibanaDashboardTitle)
                && Objects.equals(kibanaDashboardUrlParameters, that.kibanaDashboardUrlParameters)
                && Objects.equals(elasticLogsBackend, that.elasticLogsBackend);
    }

    /**
     * Returns hash code for backend configuration.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                displayKibanaDashboardLink,
                kibanaBaseUrl,
                kibanaSpaceIdentifier,
                kibanaDashboardTitle,
                kibanaDashboardUrlParameters,
                elasticLogsBackend);
    }

    /** Descriptor for the Elastic Observability backend configuration in the Jenkins UI. */
    @Extension
    @Symbol("elastic")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {

        /**
         * Returns display name used in Jenkins backend selector.
         *
         * @return backend display name
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return DEFAULT_BACKEND_NAME;
        }

        /**
         * Returns default dashboard query parameters.
         *
         * @return default dashboard query parameters
         */
        public String getDefaultKibanaDashboardUrlParameters() {
            return DEFAULT_KIBANA_DASHBOARD_QUERY_PARAMETERS;
        }

        /**
         * Returns default dashboard title.
         *
         * @return default dashboard title
         */
        public String getDefaultKibanaDashboardTitle() {
            return DEFAULT_KIBANA_DASHBOARD_TITLE;
        }

        /**
         * Returns default Kibana space identifier.
         *
         * @return default space identifier
         */
        public String getDefaultKibanaSpaceIdentifier() {
            return DEFAULT_KIBANA_SPACE_IDENTIFIER;
        }

        /**
         * Validates Kibana base URL entered in global configuration.
         *
         * @param kibanaBaseUrl user-provided Kibana base URL
         * @return validation result
         */
        public FormValidation doCheckKibanaBaseUrl(@QueryParameter("kibanaBaseUrl") String kibanaBaseUrl) {
            if (StringUtils.isEmpty(kibanaBaseUrl)) {
                return FormValidation.ok();
            }
            try {
                new URI(kibanaBaseUrl).toURL();
            } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
                return FormValidation.error("Invalid URL: " + e.getMessage());
            }
            return FormValidation.ok();
        }
    }

    /**
     * List the attribute keys of the template bindings exposed by {@link ObservabilityBackend#getBindings()}
     */
    public interface TemplateBindings extends ObservabilityBackend.TemplateBindings {
        String KIBANA_BASE_URL = "kibanaBaseUrl";
        String KIBANA_DASHBOARD_TITLE = "kibanaDashboardTitle";
        String KIBANA_SPACE_IDENTIFIER = "kibanaSpaceIdentifier";
    }

    /**
     * Returns the configured Elastic backend, if present.
     *
     * @return the configured {@link ElasticBackend}, or an empty optional when not configured
     */
    public static Optional<ElasticBackend> get() {
        Optional<ElasticBackend> ret = null;
        final JenkinsOpenTelemetryPluginConfiguration configuration =
                GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class);
        if (configuration != null) {
            Optional<ObservabilityBackend> backend = configuration.getObservabilityBackends().stream()
                    .filter(x -> x instanceof ElasticBackend)
                    .findFirst();
            if (!backend.isEmpty()) {
                ret = Optional.of((ElasticBackend) backend.get());
            }
        }
        return ret;
    }
}
