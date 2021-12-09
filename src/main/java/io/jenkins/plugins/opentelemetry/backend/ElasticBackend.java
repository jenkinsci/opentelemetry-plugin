/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import hudson.Extension;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class ElasticBackend extends ObservabilityBackend {

    private final static Logger LOGGER = Logger.getLogger(ElasticBackend.class.getName());

    public static final String OTEL_ELASTIC_URL = "OTEL_ELASTIC_URL";
    public static final String DEFAULT_BACKEND_NAME = "Elastic Observability";
    public static String DEFAULT_KIBANA_DASHBOARD_TITLE = "CI/CD Overview";
    public static String DEFAULT_KIBANA_SPACE_IDENTIFIER = "";

    private String kibanaBaseUrl;

    /**
     * See https://www.elastic.co/guide/en/kibana/master/xpack-spaces.html
     */
    private String kibanaSpaceIdentifier;

    private String kibanaDashboardTitle;

    @DataBoundConstructor
    public ElasticBackend() {

    }

    @Override
    public Map<String, Object> mergeBindings(Map<String, Object> bindings) {
        Map<String, Object> mergedBindings = new HashMap<>(bindings);
        mergedBindings.put("kibanaBaseUrl", this.kibanaBaseUrl);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElasticBackend that = (ElasticBackend) o;
        return Objects.equals(kibanaBaseUrl, that.kibanaBaseUrl) && Objects.equals(kibanaSpaceIdentifier, that.kibanaSpaceIdentifier) && Objects.equals(kibanaDashboardTitle, that.kibanaDashboardTitle);
    }

    @Override
    public int hashCode() {
        return ElasticBackend.class.hashCode();
    }

    @Extension
    @Symbol("elastic")
    public static class DescriptorImpl extends ObservabilityBackendDescriptor {
        @Override
        public String getDisplayName() {
            return DEFAULT_BACKEND_NAME;
        }
    }
}
