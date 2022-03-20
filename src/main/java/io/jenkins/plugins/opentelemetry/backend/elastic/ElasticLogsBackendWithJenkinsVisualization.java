/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import groovy.text.Template;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.opentelemetry.jenkins.CredentialsNotFoundException;
import io.jenkins.plugins.opentelemetry.jenkins.JenkinsCredentialsToApacheHttpCredentialsAdapter;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.opentelemetry.api.trace.Tracer;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.http.auth.Credentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.NoSuchElementException;
import java.util.Objects;

public class ElasticLogsBackendWithJenkinsVisualization extends ElasticLogsBackend {
    private String elasticsearchUrl;
    private String elasticsearchCredentialsId;

    @DataBoundConstructor
    public ElasticLogsBackendWithJenkinsVisualization() {

    }

    @Override
    public LogStorageRetriever getLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
        Template buildLogsVisualizationUrlTemplate = getBuildLogsVisualizationUrlTemplate();
        if (StringUtils.isBlank(elasticsearchUrl)) {
            return null; // FIXME handle case where this logs retriever is miss configured lacking of an Elasticsearch URL. We should use the rendering  ElasticLogsBackendWithVisualizationOnlyThroughKibana
        } else {
            Tracer tracer = OpenTelemetrySdkProvider.get().getTracer();
            Credentials credentials = new JenkinsCredentialsToApacheHttpCredentialsAdapter(() -> elasticsearchCredentialsId);
            return new ElasticsearchLogStorageRetriever(
                this.elasticsearchUrl, credentials,
                buildLogsVisualizationUrlTemplate, templateBindingsProvider,
                tracer);
        }
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
        ElasticLogsBackendWithJenkinsVisualization that = (ElasticLogsBackendWithJenkinsVisualization) o;
        return Objects.equals(elasticsearchUrl, that.elasticsearchUrl) && Objects.equals(elasticsearchCredentialsId, that.elasticsearchCredentialsId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elasticsearchUrl, elasticsearchCredentialsId);
    }

    @Override
    public String toString() {
        return "ElasticLogsBackendWithVisualizationJenkins{" +
            "elasticsearchUrl='" + elasticsearchUrl + '\'' +
            ", elasticsearchCredentialsId='" + elasticsearchCredentialsId + '\'' +
            '}';
    }

    @Extension(ordinal = 0)
    public static class DescriptorImpl extends ElasticLogsBackend.DescriptorImpl {
        private static final String ERROR_MALFORMED_URL = "The url is malformed.";
        @Override
        public String getDisplayName() {
            return "Store pipeline logs In Elastic and visualize logs both in Elastic and through Jenkins";
        }

        public FormValidation doCheckElasticsearchUrl(@QueryParameter("elasticsearchUrl") String url) {
            if (StringUtils.isEmpty(url)) {
                return FormValidation.ok();
            }
            try {
                new URL(url);
            } catch (MalformedURLException e) {
                return FormValidation.error("Invalid URL: " + e.getMessage());
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillElasticsearchCredentialsIdItems(Item context, @QueryParameter String elasticsearchCredentialsId) {
            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                || context != null && !context.hasPermission(context.CONFIGURE)) {
                return new StandardListBoxModel();
            }

            return new StandardListBoxModel().includeEmptyValue()
                .includeAs(ACL.SYSTEM, context, StandardUsernameCredentials.class)
                .includeCurrentValue(elasticsearchCredentialsId);
        }

        public FormValidation doCheckElasticsearchCredentialsId(Item context, @QueryParameter String elasticsearchCredentialsId) {
            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                || context != null && !context.hasPermission(context.CONFIGURE)) {
                return FormValidation.ok();
            }

            if (elasticsearchCredentialsId == null || elasticsearchCredentialsId.isEmpty()) {
                return FormValidation.error("Elasticsearch credentials are missing");
            }
            try {
                new JenkinsCredentialsToApacheHttpCredentialsAdapter(() -> elasticsearchCredentialsId).getUserPrincipal().getName();
            } catch (CredentialsNotFoundException e) {
                return FormValidation.error("Elasticsearch credentials are not valid");
            }
            return FormValidation.ok();
        }

        public FormValidation doValidate(@QueryParameter String elasticsearchUrl, @QueryParameter String elasticsearchCredentialsId) {
            FormValidation elasticsearchUrlValidation = doCheckElasticsearchUrl(elasticsearchUrl);
            if (elasticsearchUrlValidation.kind != FormValidation.Kind.OK) {
                return elasticsearchUrlValidation;
            }

            try {
                Tracer tracer = OpenTelemetrySdkProvider.get().getTracer();
                Credentials credentials = new JenkinsCredentialsToApacheHttpCredentialsAdapter(() -> elasticsearchCredentialsId);
                ElasticsearchLogStorageRetriever elasticsearchLogStorageRetriever = new ElasticsearchLogStorageRetriever(
                    elasticsearchUrl,
                    credentials,
                    ObservabilityBackend.ERROR_TEMPLATE, // TODO cleanup code, we shouldn't have to instantiate the ElasticsearchLogStorageRetriever to check the proper configuration of the access to Elasticsearch
                    TemplateBindingsProvider.empty(),
                    tracer);

                return FormValidation.aggregate(elasticsearchLogStorageRetriever.checkElasticsearchSetup());
            } catch (NoSuchElementException e) {
                return FormValidation.error("No credentials found for id '" + elasticsearchCredentialsId + "'");
            } catch (Exception e) {
                return FormValidation.error(e, e.getMessage());
            }
        }
    }
}
