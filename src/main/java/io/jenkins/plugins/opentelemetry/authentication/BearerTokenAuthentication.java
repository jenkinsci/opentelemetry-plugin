/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.authentication;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Extension;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * See https://tools.ietf.org/html/rfc6750
 */
@Extension
public class BearerTokenAuthentication extends OtlpAuthentication {
    private final static Logger LOGGER = Logger.getLogger(BearerTokenAuthentication.class.getName());

    private String tokenId;

    @DataBoundConstructor
    public BearerTokenAuthentication() {
    }

    public BearerTokenAuthentication(String tokenId) {
        this.tokenId = tokenId;
    }

    private String getAuthenticationHeaderValue() {
        StringCredentials credentials = (StringCredentials) CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StringCredentials.class, Jenkins.get(),
                        ACL.SYSTEM, Collections.EMPTY_LIST),
                CredentialsMatchers.withId(this.tokenId));
        String authenticationTokenValue;
        if (credentials == null) {
            // TODO better handling
            LOGGER.log(Level.WARNING, () -> "StringCredentials with id `" + tokenId + "` not found. Fall back to empty secret, an authentication error is likely to happen.");
            authenticationTokenValue = "";
        } else {
            authenticationTokenValue = Secret.toString(credentials.getSecret());
        }
        return authenticationTokenValue;
    }

    @Override
    public void enrichOpenTelemetryAutoConfigureConfigProperties(Map<String, String> configProperties) {
        // TODO don't overwrite 'otel.exporter.otlp.headers' if already defined, just append to it
        configProperties.put("otel.exporter.otlp.headers", "Authorization=Bearer " + this.getAuthenticationHeaderValue());
    }

    @Override
    public void enrichOtelEnvironmentVariables(Map<String, String> environmentVariables) {
        // TODO don't overwrite OTEL_EXPORTER_OTLP_HEADERS if already defined, just append to it
        environmentVariables.put("OTEL_EXPORTER_OTLP_HEADERS", "authorization=Bearer " + this.getAuthenticationHeaderValue());
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
        return "BearerTokenAuthentication{" +
                "tokenId='" + tokenId + '\'' +
                '}';
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
                        ACL.SYSTEM,
                        Jenkins.get(),
                        StringCredentials.class,
                        Collections.<DomainRequirement>emptyList(),
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
                            ACL.SYSTEM,
                            Jenkins.get(),
                            StringCredentials.class,
                            Collections.<DomainRequirement>emptyList(),
                            CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StringCredentials.class)));
        }
    }
}
