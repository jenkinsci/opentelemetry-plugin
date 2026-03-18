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
        String bearerHeader = "Authorization=Bearer " + this.getAuthenticationHeaderValue();
        String existing = configProperties.get(OTEL_EXPORTER_OTLP_HEADERS.asProperty());
        if (existing != null && !existing.isEmpty()) {
            configProperties.put(OTEL_EXPORTER_OTLP_HEADERS.asProperty(), existing + "," + bearerHeader);
        } else {
            configProperties.put(OTEL_EXPORTER_OTLP_HEADERS.asProperty(), bearerHeader);
        }
    }

    @Override
    public void enrichOtelEnvironmentVariables(Map<String, String> environmentVariables) {
        String bearerHeader = "authorization=Bearer " + this.getAuthenticationHeaderValue();
        String existing = environmentVariables.get(OTEL_EXPORTER_OTLP_HEADERS.asEnvVar());
        if (existing != null && !existing.isEmpty()) {
            environmentVariables.put(OTEL_EXPORTER_OTLP_HEADERS.asEnvVar(), existing + "," + bearerHeader);
        } else {
            environmentVariables.put(OTEL_EXPORTER_OTLP_HEADERS.asEnvVar(), bearerHeader);
        }
    }

    public String getTokenId() {
        return tokenId;
    }

    @DataBoundSetter
    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    @Override
    public String toString() {
        return "BearerTokenAuthentication{" + "tokenId='" + tokenId + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BearerTokenAuthentication that = (BearerTokenAuthentication) o;
        return Objects.equals(tokenId, that.tokenId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenId);
    }

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
        @Override
        public String getDisplayName() {
            return "Bearer Token Authentication";
        }

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
