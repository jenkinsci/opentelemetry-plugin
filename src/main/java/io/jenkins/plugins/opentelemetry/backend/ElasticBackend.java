/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import groovy.text.GStringTemplateEngine;
import groovy.text.Template;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticsearchLogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.opentelemetry.api.trace.Tracer;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class ElasticBackend extends ObservabilityBackend {

    private final static Logger LOGGER = Logger.getLogger(ElasticBackend.class.getName());

    public static final String OTEL_ELASTIC_URL = "OTEL_ELASTIC_URL";
    public static final String DEFAULT_BACKEND_NAME = "Elastic Observability";
    public static final String DEFAULT_KIBANA_DASHBOARD_TITLE = "Jenkins Overview";
    public static final String DEFAULT_KIBANA_SPACE_IDENTIFIER = "";
    public static final String DEFAULT_KIBANA_DASHBOARD_QUERY_PARAMETERS = "title=${kibanaDashboardTitle}&" +
        "_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-24h%2Fh,to:now))";

    private boolean displayKibanaDashboardLink;

    private String kibanaBaseUrl;

    /**
     * See https://www.elastic.co/guide/en/kibana/master/xpack-spaces.html
     */
    private String kibanaSpaceIdentifier;

    private String kibanaDashboardTitle;

    private String kibanaDashboardUrlParameters;

    @CheckForNull
    private String elasticsearchUrl;
    @CheckForNull
    private String elasticsearchCredentialsId;

    private Template buildLogsVisualizationUrlGTemplate;

    @DataBoundConstructor
    public ElasticBackend() {

    }

    @Override
    public Map<String, Object> mergeBindings(Map<String, Object> bindings) {
        Map<String, Object> mergedBindings = new HashMap<>(bindings);
        mergedBindings.put("kibanaBaseUrl", this.getKibanaBaseUrl());
        mergedBindings.put("kibanaDashboardTitle", this.kibanaDashboardTitle);
        mergedBindings.put("kibanaSpaceIdentifier", this.kibanaSpaceIdentifier);
        return mergedBindings;
    }

    @CheckForNull
    @Override
    public String getTraceVisualisationUrlTemplate() {
        return "${kibanaBaseUrl}/app/apm/services/${serviceName}/transactions/view" +
            "?rangeFrom=${startTime.minusSeconds(600)}" +
            "&rangeTo=${startTime.plusSeconds(600)}" +
            "&transactionName=${rootSpanName}" +
            "&transactionType=job" + // see io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes.ELASTIC_TRANSACTION_TYPE
            "&latencyAggregationType=avg" +
            "&traceId=${traceId}" +
            "&transactionId=${spanId}";
    }

    public String getKibanaBaseUrl() {
        if (kibanaBaseUrl != null && kibanaBaseUrl.endsWith("/")) {
            kibanaBaseUrl = kibanaBaseUrl.substring(0, kibanaBaseUrl.length()-1);
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
        return "/plugin/opentelemetry/images/48x48/elastic.png";
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
        try {
            String kibanaSpaceBaseUrl;
            if (StringUtils.isBlank(this.getKibanaSpaceIdentifier())) {
                kibanaSpaceBaseUrl = "${kibanaBaseUrl}";
            } else {
                kibanaSpaceBaseUrl = "${kibanaBaseUrl}/s/" + URLEncoder.encode(this.getKibanaSpaceIdentifier(), StandardCharsets.UTF_8.name());
            }
            return kibanaSpaceBaseUrl + "/app/kibana#/dashboards?" +
                "title=" + URLEncoder.encode(getKibanaDashboardTitle(), StandardCharsets.UTF_8.name()) + "&" +
                "_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-24h%2Fh,to:now))";
        } catch (UnsupportedEncodingException e) {
            LOGGER.log(Level.INFO,
                "Exception formatting Kibana URL with kibanaSpaceIdentifier=" + getKibanaSpaceIdentifier() +
                    ", kibanaDashboardName=" + getKibanaDashboardTitle() + ": " + e);
            return null;
        }
    }

    public Template getBuildLogsVisualizationUrlTemplate() {
        // see https://www.elastic.co/guide/en/kibana/6.8/sharing-dashboards.html

        if (this.buildLogsVisualizationUrlGTemplate == null) {
            try {
                String kibanaSpaceBaseUrl;
                if (StringUtils.isBlank(this.getKibanaSpaceIdentifier())) {
                    kibanaSpaceBaseUrl = "${kibanaBaseUrl}";
                } else {
                    kibanaSpaceBaseUrl = "${kibanaBaseUrl}/s/" + URLEncoder.encode(this.getKibanaSpaceIdentifier(), StandardCharsets.UTF_8.name());
                }

                String urlTemplate = kibanaSpaceBaseUrl + "/app/logs/stream?" +
                    "logPosition=(end:now,start:now-1d,streamLive:!f)&" +
                    "logFilter=(language:kuery,query:%27trace.id:${traceId}%27)&";
                GStringTemplateEngine gStringTemplateEngine = new GStringTemplateEngine();
                try {
                    this.buildLogsVisualizationUrlGTemplate = gStringTemplateEngine.createTemplate(urlTemplate);
                } catch (IOException | ClassNotFoundException e) {
                    LOGGER.log(Level.WARNING, "Invalid Trace Visualisation URL Template '" + this.getTraceVisualisationUrlTemplate() + "'", e);
                    this.buildLogsVisualizationUrlGTemplate = ERROR_TEMPLATE;
                }
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
        return buildLogsVisualizationUrlGTemplate;
    }

    @Nullable
    @Override
    public LogStorageRetriever getLogStorageRetriever() {
        if (!isSendBuildLogsThroughOpenTelemetry()) {
            return null;
        }
        Template buildLogsVisualizationUrlTemplate = getBuildLogsVisualizationUrlTemplate();
        if (StringUtils.isBlank(elasticsearchUrl)) {
            return null; // FIXME TODO
        } else {
            Tracer tracer = OpenTelemetrySdkProvider.get().getTracer();
            return new ElasticsearchLogStorageRetriever(this.elasticsearchUrl, ElasticsearchLogStorageRetriever.getCredentials(this.elasticsearchCredentialsId), buildLogsVisualizationUrlTemplate, tracer);
        }
    }

    public String getKibanaSpaceIdentifier() {
        return Objects.toString(kibanaSpaceIdentifier, DEFAULT_KIBANA_SPACE_IDENTIFIER);
    }

    @DataBoundSetter
    public void setKibanaSpaceIdentifier(String kibanaSpaceIdentifier) {
        this.kibanaSpaceIdentifier = kibanaSpaceIdentifier;
    }

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

    @DataBoundSetter
    public void setElasticsearchCredentialsId(@CheckForNull String elasticsearchCredentialsId) {
        this.elasticsearchCredentialsId = elasticsearchCredentialsId;
    }

    @CheckForNull
    public String getElasticsearchCredentialsId() {
        return elasticsearchCredentialsId;
    }

    @CheckForNull
    public String getElasticsearchUrl() {
        return elasticsearchUrl;
    }

    @DataBoundSetter
    public void setElasticsearchUrl(@CheckForNull String elasticsearchUrl) {
        this.elasticsearchUrl = Util.fixNull(elasticsearchUrl);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElasticBackend that = (ElasticBackend) o;
        return displayKibanaDashboardLink == that.displayKibanaDashboardLink &&
            Objects.equals(kibanaBaseUrl, that.kibanaBaseUrl) &&
            Objects.equals(kibanaSpaceIdentifier, that.kibanaSpaceIdentifier) &&
            Objects.equals(kibanaDashboardTitle, that.kibanaDashboardTitle) &&
            Objects.equals(kibanaDashboardUrlParameters, that.kibanaDashboardUrlParameters)
            && Objects.equals(elasticsearchUrl, that.elasticsearchUrl)
            && Objects.equals(elasticsearchCredentialsId, that.elasticsearchCredentialsId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayKibanaDashboardLink, kibanaBaseUrl, kibanaSpaceIdentifier, kibanaDashboardTitle, kibanaDashboardUrlParameters, elasticsearchUrl, elasticsearchCredentialsId);
    }

    @Extension
    @Symbol("elastic")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {
        private static final String ERROR_MALFORMED_URL = "The url is malformed.";

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

        @RequirePOST
        public FormValidation doCheckKibanaUrl(@QueryParameter("kibanaBaseUrl") String url) {
            if (StringUtils.isEmpty(url)) {
                return FormValidation.ok();
            }
            try {
                new URL(url);
            } catch (MalformedURLException e) {
                return FormValidation.error(ERROR_MALFORMED_URL, e);
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public FormValidation doCheckElasticsearchUrl(@QueryParameter("elasticsearchUrl") String url) {
            if (StringUtils.isEmpty(url)) {
                return FormValidation.ok();
            }
            try {
                new URL(url);
            } catch (MalformedURLException e) {
                return FormValidation.error(ERROR_MALFORMED_URL, e);
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public ListBoxModel doFillElasticsearchCredentialsIdItems(Item context, @QueryParameter String elasticsearchCredentialsId) {
            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                || context != null && !context.hasPermission(context.CONFIGURE)) {
                return new StandardListBoxModel();
            }

            return new StandardListBoxModel().includeEmptyValue()
                .includeAs(ACL.SYSTEM, context, StandardUsernameCredentials.class)
                .includeCurrentValue(elasticsearchCredentialsId);
        }

        @RequirePOST
        public FormValidation doCheckElasticsearchCredentialsId(Item context, @QueryParameter String elasticsearchCredentialsId) {
            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                || context != null && !context.hasPermission(context.CONFIGURE)) {
                return FormValidation.ok();
            }

            try {
                ElasticsearchLogStorageRetriever.getCredentials(elasticsearchCredentialsId);
            } catch (NoSuchElementException e) {
                return FormValidation.warning("The credentials are not valid.");
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public FormValidation doValidate(@QueryParameter String elasticsearchUrl, @QueryParameter String elasticsearchCredentialsId, @QueryParameter String kibanaBaseUrl) {
            FormValidation elasticsearchUrlValidation = doCheckElasticsearchUrl(elasticsearchUrl);
            if (elasticsearchUrlValidation.kind != FormValidation.Kind.OK) {
                return elasticsearchUrlValidation;
            }

            try {
                Tracer tracer = OpenTelemetrySdkProvider.get().getTracer();
                ElasticsearchLogStorageRetriever elasticsearchLogStorageRetriever = new ElasticsearchLogStorageRetriever(
                    elasticsearchUrl,
                    ElasticsearchLogStorageRetriever.getCredentials(elasticsearchCredentialsId),
                    ERROR_TEMPLATE, // TODO cleanup code, we shouldn't have to instantiate the ElasticsearchLogStorageRetriever to check index existence
                    tracer);

                if (elasticsearchLogStorageRetriever.indexTemplateExists()) {
                    return FormValidation.ok("success");
                } else {
                    return FormValidation.error("Logs index pattern not found. Verify you use Elastic 8.0+ with data streams for APM");
                }
            } catch (NoSuchElementException e) {
                return FormValidation.error("Invalid credentials.");
            } catch (IllegalArgumentException e) {
                return FormValidation.error(e, e.getMessage());
            } catch (IOException e) {
                return FormValidation.error(e, e.getMessage());
            } catch (Exception e) {
                return FormValidation.error(e, e.getMessage());
            }
        }
    }
}
