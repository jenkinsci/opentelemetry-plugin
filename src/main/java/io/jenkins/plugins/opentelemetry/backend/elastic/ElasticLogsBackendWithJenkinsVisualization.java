/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.CheckForNull;
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
import io.jenkins.plugins.opentelemetry.jenkins.HttpAuthHeaderFactory;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class ElasticLogsBackendWithJenkinsVisualization extends ElasticLogsBackend {
    private static final String MSG_ELASTICSEARCH_URL_IS_BLANK =
            "Elasticsearch URL is blank, logs will not be stored in Elasticsearch";

    private static final Logger logger = Logger.getLogger(ElasticLogsBackendWithJenkinsVisualization.class.getName());

    private String elasticsearchUrl;
    private boolean disableSslVerifications;
    private String elasticsearchCredentialsId;

    /**
     * Creates Elastic logs backend configuration with defaults.
     */
    @DataBoundConstructor
    public ElasticLogsBackendWithJenkinsVisualization() {}

    /**
     * Creates a log storage retriever backed by Elasticsearch.
     *
     * @param templateBindingsProvider template bindings provider
     * @return Elasticsearch log storage retriever
     * @throws IllegalStateException when Elasticsearch URL is not configured
     */
    @Override
    @MustBeClosed
    public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
        Template buildLogsVisualizationUrlTemplate = getBuildLogsVisualizationUrlTemplate();
        if (StringUtils.isBlank(elasticsearchUrl)) {
            logger.warning(MSG_ELASTICSEARCH_URL_IS_BLANK);
            throw new IllegalStateException(MSG_ELASTICSEARCH_URL_IS_BLANK);
        } else {
            return new ElasticsearchLogStorageRetriever(
                    this.elasticsearchUrl,
                    disableSslVerifications,
                    elasticsearchCredentialsId,
                    buildLogsVisualizationUrlTemplate,
                    templateBindingsProvider);
        }
    }

    /**
     * Sets Elasticsearch credentials identifier.
     *
     * @param elasticsearchCredentialsId credentials identifier
     */
    @DataBoundSetter
    public void setElasticsearchCredentialsId(@CheckForNull String elasticsearchCredentialsId) {
        this.elasticsearchCredentialsId = elasticsearchCredentialsId;
    }

    /**
     * Returns Elasticsearch credentials identifier.
     *
     * @return credentials identifier, or {@code null} if not configured
     */
    @CheckForNull
    public String getElasticsearchCredentialsId() {
        return elasticsearchCredentialsId;
    }

    /**
     * Returns Elasticsearch base URL.
     *
     * @return Elasticsearch base URL, or {@code null} if not configured
     */
    @CheckForNull
    public String getElasticsearchUrl() {
        return elasticsearchUrl;
    }

    /**
     * Sets Elasticsearch base URL.
     *
     * @param elasticsearchUrl Elasticsearch base URL
     */
    @DataBoundSetter
    public void setElasticsearchUrl(@CheckForNull String elasticsearchUrl) {
        this.elasticsearchUrl = Util.fixNull(elasticsearchUrl);
    }

    /**
     * Returns whether SSL verification is disabled for Elasticsearch connections.
     *
     * @return {@code true} when SSL verification is disabled
     */
    public boolean isDisableSslVerifications() {
        return disableSslVerifications;
    }

    @DataBoundSetter
    /**
     * Sets whether SSL verification is disabled for Elasticsearch connections.
     *
     * @param disableSslVerifications disable SSL verification flag
     */
    public void setDisableSslVerifications(boolean disableSslVerifications) {
        this.disableSslVerifications = disableSslVerifications;
    }

    @Override
    /**
     * Compares backend configuration values for equality.
     *
     * @param o object to compare
     * @return {@code true} when configurations are equivalent
     */
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElasticLogsBackendWithJenkinsVisualization that = (ElasticLogsBackendWithJenkinsVisualization) o;
        return Objects.equals(elasticsearchUrl, that.elasticsearchUrl)
                && Objects.equals(disableSslVerifications, that.disableSslVerifications)
                && Objects.equals(elasticsearchCredentialsId, that.elasticsearchCredentialsId);
    }

    @Override
    /**
     * Returns hash code for backend configuration.
     *
     * @return hash code
     */
    public int hashCode() {
        return Objects.hash(elasticsearchUrl, disableSslVerifications, elasticsearchCredentialsId);
    }

    @Override
    /**
     * Returns string representation for diagnostics.
     *
     * @return printable backend configuration
     */
    public String toString() {
        return "ElasticLogsBackendWithVisualizationJenkins{" + "elasticsearchUrl='"
                + elasticsearchUrl + '\'' + ", disableSslVerifications='"
                + disableSslVerifications + '\'' + ", elasticsearchCredentialsId='"
                + elasticsearchCredentialsId + '\'' + '}';
    }

    @Extension(ordinal = 0)
    public static class DescriptorImpl extends ElasticLogsBackend.DescriptorImpl {
        @Override
        /**
         * Returns display name used in global configuration.
         *
         * @return logs backend display name
         */
        public String getDisplayName() {
            return "Store pipeline logs In Elastic and visualize logs both in Elastic and through Jenkins";
        }

        @RequirePOST
        public FormValidation doCheckElasticsearchUrl(@QueryParameter("elasticsearchUrl") String url) {
            if (!isAuthorized()) {
                return FormValidation.error("You do not have permission to configure this setting.");
            }
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

        /**
         * Populates credentials selector for Elasticsearch authentication.
         *
         * @param elasticsearchCredentialsId currently selected credentials id
         * @return credentials list box model
         */
        @RequirePOST
        public ListBoxModel doFillElasticsearchCredentialsIdItems(@QueryParameter String elasticsearchCredentialsId) {
            if (!isAuthorized()) {
                return new StandardListBoxModel();
            }

            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeAs(ACL.SYSTEM2, (Item) null, StandardUsernameCredentials.class)
                    .includeCurrentValue(elasticsearchCredentialsId);
        }

        @RequirePOST
        /**
         * Validates selected Elasticsearch credentials.
         *
         * @param elasticsearchCredentialsId credentials id
         * @return validation result
         */
        public FormValidation doCheckElasticsearchCredentialsId(@QueryParameter String elasticsearchCredentialsId) {
            if (!isAuthorized()) {
                return FormValidation.error("You do not have permission to configure this setting.");
            }

            if (elasticsearchCredentialsId == null || elasticsearchCredentialsId.isEmpty()) {
                return FormValidation.error("Elasticsearch credentials are missing");
            }
            try {
                new HttpAuthHeaderFactory(elasticsearchCredentialsId).createAuthHeader();
            } catch (CredentialsNotFoundException e) {
                return FormValidation.error("Elasticsearch credentials are not valid");
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public FormValidation doValidate(
                @QueryParameter String elasticsearchUrl,
                @QueryParameter boolean disableSslVerifications,
                @QueryParameter String elasticsearchCredentialsId) {
            if (!isAuthorized()) {
                return FormValidation.error("You do not have permission to configure this setting.");
            }
            FormValidation elasticsearchUrlValidation = doCheckElasticsearchUrl(elasticsearchUrl);
            if (elasticsearchUrlValidation.kind != FormValidation.Kind.OK) {
                return elasticsearchUrlValidation;
            }
            try (ElasticsearchLogStorageRetriever elasticsearchLogStorageRetriever =
                    new ElasticsearchLogStorageRetriever(
                            elasticsearchUrl,
                            disableSslVerifications,
                            elasticsearchCredentialsId,
                            ObservabilityBackend.ERROR_TEMPLATE,
                            TemplateBindingsProvider.empty())) {

                return FormValidation.aggregate(elasticsearchLogStorageRetriever.checkElasticsearchSetup());
            } catch (NoSuchElementException e) {
                return FormValidation.error("No credentials found for id '" + elasticsearchCredentialsId + "'");
            } catch (Exception e) {
                return FormValidation.error(e, e.getMessage());
            }
        }

        /**
         * Checks if the user has permission to configure the backend.
         * @return true if the user is authorized, false otherwise
         */
        private boolean isAuthorized() {
            return Jenkins.get().hasPermission(Jenkins.ADMINISTER);
        }
    }
}
