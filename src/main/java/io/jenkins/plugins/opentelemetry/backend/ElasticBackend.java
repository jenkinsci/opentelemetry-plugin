/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import java.util.HashMap;
import java.util.Map;

@Extension
public class ElasticBackend extends ObservabilityBackend {

    public static final String OTEL_ELASTIC_URL = "OTEL_ELASTIC_URL";
    public static final String DEFAULT_NAME = "Elastic Observability";
    private String kibanaBaseUrl;

    @DataBoundConstructor
    public ElasticBackend(){

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
                "&transactionType=unknown" + // see io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes.ELASTIC_TRANSACTION_TYPE
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
        return DEFAULT_NAME;
    }

    @CheckForNull
    @Override
    public String getMetricsVisualisationUrlTemplate() {
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof ElasticBackend;
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
            return DEFAULT_NAME;
        }
    }
}
