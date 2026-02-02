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

    public static final String OTEL_ELASTIC_URL = "OTEL_ELASTIC_URL";
    public static final String DEFAULT_BACKEND_NAME = "Elastic Observability";
    public static final String DEFAULT_KIBANA_DASHBOARD_TITLE = "Jenkins Overview";
    public static final String DEFAULT_KIBANA_SPACE_IDENTIFIER = "";
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

    @DataBoundConstructor
    public ElasticBackend() {}

    @Override
    public Map<String, Object> mergeBindings(Map<String, Object> bindings) {
        Map<String, Object> mergedBindings = new HashMap<>(bindings);
        mergedBindings.putAll(getBindings());
        return mergedBindings;
    }

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

    @CheckForNull
    public String getKibanaBaseUrl() {
        if (kibanaBaseUrl != null && kibanaBaseUrl.endsWith("/")) {
            kibanaBaseUrl = kibanaBaseUrl.substring(0, kibanaBaseUrl.length() - 1);
        }
        return kibanaBaseUrl;
    }

    @DataBoundSetter
    public void setKibanaBaseUrl(String kibanaBaseUrl) {
        this.kibanaBaseUrl = kibanaBaseUrl;
    }

    @CheckForNull
    @Override
    public String getIconPath() {
        return "icon-otel-elastic";
    }

    @CheckForNull
    @Override
    public String getEnvVariableName() {
        return OTEL_ELASTIC_URL;
    }

    @CheckForNull
    @Override
    public String getDefaultName() {
        return DEFAULT_BACKEND_NAME;
    }

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

    public ElasticLogsBackend getElasticLogsBackend() {
        return elasticLogsBackend;
    }

    @DataBoundSetter
    public void setElasticLogsBackend(ElasticLogsBackend elasticLogsBackend) {
        this.elasticLogsBackend = elasticLogsBackend;
    }

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

    @NonNull
    @Override
    public Map<String, String> getOtelConfigurationProperties() {
        // FIXME related to https://github.com/jenkinsci/opentelemetry-plugin/issues/683
        if (elasticLogsBackend == null) {
            return Collections.emptyMap();
        } else {
            return elasticLogsBackend.getOtelConfigurationProperties();
        }
    }

    @NonNull
    public String getKibanaSpaceIdentifier() {
        return Objects.toString(kibanaSpaceIdentifier, DEFAULT_KIBANA_SPACE_IDENTIFIER);
    }

    @DataBoundSetter
    public void setKibanaSpaceIdentifier(String kibanaSpaceIdentifier) {
        this.kibanaSpaceIdentifier = kibanaSpaceIdentifier;
    }

    @NonNull
    public String getKibanaDashboardTitle() {
        return Objects.toString(kibanaDashboardTitle, DEFAULT_KIBANA_DASHBOARD_TITLE);
    }

    @DataBoundSetter
    public void setKibanaDashboardTitle(String kibanaDashboardTitle) {
        this.kibanaDashboardTitle = kibanaDashboardTitle;
    }

    public String getKibanaDashboardUrlParameters() {
        return Objects.toString(kibanaDashboardUrlParameters, DEFAULT_KIBANA_DASHBOARD_QUERY_PARAMETERS);
    }

    @DataBoundSetter
    public void setKibanaDashboardUrlParameters(String kibanaDashboardUrlParameters) {
        this.kibanaDashboardUrlParameters = kibanaDashboardUrlParameters;
    }

    public boolean isDisplayKibanaDashboardLink() {
        return displayKibanaDashboardLink;
    }

    @DataBoundSetter
    public void setDisplayKibanaDashboardLink(boolean displayKibanaDashboardLink) {
        this.displayKibanaDashboardLink = displayKibanaDashboardLink;
    }

    public boolean isEnableEDOT() {
        return enableEDOT;
    }

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

    @Extension
    @Symbol("elastic")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return DEFAULT_BACKEND_NAME;
        }

        public String getDefaultKibanaDashboardUrlParameters() {
            return DEFAULT_KIBANA_DASHBOARD_QUERY_PARAMETERS;
        }

        public String getDefaultKibanaDashboardTitle() {
            return DEFAULT_KIBANA_DASHBOARD_TITLE;
        }

        public String getDefaultKibanaSpaceIdentifier() {
            return DEFAULT_KIBANA_SPACE_IDENTIFIER;
        }

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
