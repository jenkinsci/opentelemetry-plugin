/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.authentication;

import static io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey.OTEL_EXPORTER_OTLP_HEADERS;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * See https://tools.ietf.org/html/rfc6750
 */
@Extension
public class BearerTokenAuthentication extends OtlpAuthentication {
    private static final Logger LOGGER = Logger.getLogger(BearerTokenAuthentication.class.getName());

    private String tokenId;

    @DataBoundConstructor
    public BearerTokenAuthentication() {}

    public BearerTokenAuthentication(String tokenId) {
        this.tokenId = tokenId;
    }

    private String getAuthenticationHeaderValue() {
        StringCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItemGroup(
                        StringCredentials.class, Jenkins.get(), ACL.SYSTEM2, Collections.emptyList()),
                CredentialsMatchers.withId(this.tokenId));
        String authenticationTokenValue;
        if (credentials == null) {
            // TODO better handling of deleted credentials
            LOGGER.log(
                    Level.WARNING,
                    () -> "StringCredentials with id `" + tokenId
                            + "` not found. Fall back to empty secret, an authentication error is likely to happen.");
            authenticationTokenValue = "";
        } else {
            authenticationTokenValue = Secret.toString(credentials.getSecret());
        }
        return authenticationTokenValue;
    }

    @Override
    public void enrichOpenTelemetryAutoConfigureConfigProperties(Map<String, String> configProperties) {
        // TODO don't overwrite 'otel.exporter.otlp.headers' if already defined, just append to it
        configProperties.put(
                OTEL_EXPORTER_OTLP_HEADERS.asProperty(), "Authorization=Bearer " + this.getAuthenticationHeaderValue());
    }

    @Override
    public void enrichOtelEnvironmentVariables(Map<String, String> environmentVariables) {
        // TODO don't overwrite 'otel.exporter.otlp.headers' if already defined, just append to it
        environmentVariables.put(
                OTEL_EXPORTER_OTLP_HEADERS.asEnvVar(), "authorization=Bearer " + this.getAuthenticationHeaderValue());
    }

    /**
     * Returns the Jenkins credentials ID for the bearer token.
     *
     * @return credentials ID
     */
    public String getTokenId() {
        return tokenId;
    }

    /**
     * Sets the Jenkins credentials ID for the bearer token.
     *
     * @param tokenId credentials ID
     */
    @DataBoundSetter
    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    /**
     * Returns a debug-friendly representation of this authentication.
     *
     * @return textual representation
     */
    @Override
    public String toString() {
        return "BearerTokenAuthentication{" + "tokenId='" + tokenId + '\'' + '}';
    }

    /**
     * Compares bearer token authentication objects by token ID.
     *
     * @param o object to compare
     * @return {@code true} when token IDs match
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BearerTokenAuthentication that = (BearerTokenAuthentication) o;
        return Objects.equals(tokenId, that.tokenId);
    }

    /**
     * Returns hash code consistent with {@link #equals(Object)}.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(tokenId);
    }

    /**
     * Populates credential IDs for bearer token selection in configuration forms.
     *
     * @return list-box model of available string credentials
     */
    public ListBoxModel doFillTokenIdItems() {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return new StandardListBoxModel().includeCurrentValue(this.tokenId);
        }
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(
                        ACL.SYSTEM2,
                        Jenkins.get(),
                        StringCredentials.class,
                        Collections.emptyList(),
                        CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StringCredentials.class)))
                .includeCurrentValue(tokenId);
    }

    @Extension
    @Symbol("bearerTokenAuthentication")
    public static class DescriptorImpl extends AbstractDescriptor {
        /**
         * Returns the descriptor display name shown in Jenkins UI.
         *
         * @return display name
         */
        @Override
        public String getDisplayName() {
            return "Bearer Token Authentication";
        }

        /**
         * Populates credential IDs for bearer token selection in descriptor forms.
         *
         * @return list-box model of available string credentials
         */
        public ListBoxModel doFillTokenIdItems() {
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                return new StandardListBoxModel();
            }
            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM2,
                            Jenkins.get(),
                            StringCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StringCredentials.class)));
        }
    }
}
