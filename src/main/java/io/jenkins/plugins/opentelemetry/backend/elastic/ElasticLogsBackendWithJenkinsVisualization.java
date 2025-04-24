/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.apache.hc.client5.http.auth.Credentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.google.errorprone.annotations.MustBeClosed;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.text.Template;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.jenkins.CredentialsNotFoundException;
import io.jenkins.plugins.opentelemetry.jenkins.JenkinsCredentialsToApacheHttpCredentialsAdapter;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import jenkins.model.Jenkins;

public class ElasticLogsBackendWithJenkinsVisualization extends ElasticLogsBackend {
    private static final String MSG_ELASTICSEARCH_URL_IS_BLANK = "Elasticsearch URL is blank, logs will not be stored in Elasticsearch";

    private final static Logger logger = Logger.getLogger(ElasticLogsBackendWithJenkinsVisualization.class.getName());

    private String elasticsearchUrl;
    private boolean disableSslVerifications;
    private String elasticsearchCredentialsId;

    @DataBoundConstructor
    public ElasticLogsBackendWithJenkinsVisualization() {

    }

    @Override
    @MustBeClosed
    public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
        Template buildLogsVisualizationUrlTemplate = getBuildLogsVisualizationUrlTemplate();
        if (StringUtils.isBlank(elasticsearchUrl)) {
            logger.warning(MSG_ELASTICSEARCH_URL_IS_BLANK);
            throw new IllegalStateException(MSG_ELASTICSEARCH_URL_IS_BLANK);
        } else {
            Credentials credentials = new JenkinsCredentialsToApacheHttpCredentialsAdapter(elasticsearchCredentialsId);
            return new ElasticsearchLogStorageRetriever(
                    this.elasticsearchUrl, disableSslVerifications, credentials,
                    buildLogsVisualizationUrlTemplate, templateBindingsProvider);
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

    public boolean isDisableSslVerifications() {
        return disableSslVerifications;
    }

    @DataBoundSetter
    public void setDisableSslVerifications(boolean disableSslVerifications) {
        this.disableSslVerifications = disableSslVerifications;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ElasticLogsBackendWithJenkinsVisualization that = (ElasticLogsBackendWithJenkinsVisualization) o;
        return Objects.equals(elasticsearchUrl, that.elasticsearchUrl)
                && Objects.equals(disableSslVerifications, that.disableSslVerifications)
                && Objects.equals(elasticsearchCredentialsId, that.elasticsearchCredentialsId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elasticsearchUrl, disableSslVerifications, elasticsearchCredentialsId);
    }

    @Override
    public String toString() {
        return "ElasticLogsBackendWithVisualizationJenkins{" +
                "elasticsearchUrl='" + elasticsearchUrl + '\'' +
                ", disableSslVerifications='" + disableSslVerifications + '\'' +
                ", elasticsearchCredentialsId='" + elasticsearchCredentialsId + '\'' +
                '}';
    }

    @Extension(ordinal = 0)
    public static class DescriptorImpl extends ElasticLogsBackend.DescriptorImpl {
        @Override
        public String getDisplayName() {
            return "Store pipeline logs In Elastic and visualize logs both in Elastic and through Jenkins";
        }

        public FormValidation doCheckElasticsearchUrl(@QueryParameter("elasticsearchUrl") String url) {
            if (StringUtils.isEmpty(url)) {
                return FormValidation.ok();
            }
            try {
                new URI(url).toURL();
            } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
                return FormValidation.error("Invalid URL: " + e.getMessage());
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillElasticsearchCredentialsIdItems(Item context,
                @QueryParameter String elasticsearchCredentialsId) {
            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    || context != null && !context.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }

            return new StandardListBoxModel().includeEmptyValue()
                    .includeAs(ACL.SYSTEM2, context, StandardUsernameCredentials.class)
                    .includeCurrentValue(elasticsearchCredentialsId);
        }

        @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT",
            justification="We don't care about the return value, we just want to check that the credentials are valid")
        public FormValidation doCheckElasticsearchCredentialsId(Item context,
                @QueryParameter String elasticsearchCredentialsId) {
            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    || context != null && !context.hasPermission(Item.CONFIGURE)) {
                return FormValidation.ok();
            }

            if (elasticsearchCredentialsId == null || elasticsearchCredentialsId.isEmpty()) {
                return FormValidation.error("Elasticsearch credentials are missing");
            }
            try {
                new JenkinsCredentialsToApacheHttpCredentialsAdapter(elasticsearchCredentialsId)
                        .getUserPrincipal().getName();
            } catch (CredentialsNotFoundException e) {
                return FormValidation.error("Elasticsearch credentials are not valid");
            }
            return FormValidation.ok();
        }

        public FormValidation doValidate(@QueryParameter String elasticsearchUrl,
                @QueryParameter boolean disableSslVerifications, @QueryParameter String elasticsearchCredentialsId) {
            FormValidation elasticsearchUrlValidation = doCheckElasticsearchUrl(elasticsearchUrl);
            if (elasticsearchUrlValidation.kind != FormValidation.Kind.OK) {
                return elasticsearchUrlValidation;
            }
            Credentials credentials = new JenkinsCredentialsToApacheHttpCredentialsAdapter(elasticsearchCredentialsId);
            try (ElasticsearchLogStorageRetriever elasticsearchLogStorageRetriever = new ElasticsearchLogStorageRetriever(
                    elasticsearchUrl,
                    disableSslVerifications,
                    credentials,
                    ObservabilityBackend.ERROR_TEMPLATE,
                    TemplateBindingsProvider.empty())) {

                return FormValidation.aggregate(elasticsearchLogStorageRetriever.checkElasticsearchSetup());
            } catch (NoSuchElementException e) {
                return FormValidation.error("No credentials found for id '" + elasticsearchCredentialsId + "'");
            } catch (Exception e) {
                return FormValidation.error(e, e.getMessage());
            }
        }
    }
}
