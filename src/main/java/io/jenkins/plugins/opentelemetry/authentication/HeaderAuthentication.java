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

@Extension
/** OTel authentication strategy that sends a custom static header name and credential-backed value. */
public class HeaderAuthentication extends OtlpAuthentication {
    private static final Logger LOGGER = Logger.getLogger(HeaderAuthentication.class.getName());

    private String headerName;
    private String headerValueId;

    /** Creates an empty header-authentication configuration to be populated through data binding. */
    @DataBoundConstructor
    public HeaderAuthentication() {}

    private String getAuthenticationHeaderValue() {
        StringCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItemGroup(
                        StringCredentials.class, Jenkins.get(), ACL.SYSTEM2, Collections.emptyList()),
                CredentialsMatchers.withId(this.headerValueId));
        String authenticationTokenValue;
        if (credentials == null) {
            // TODO better handling
            LOGGER.log(
                    Level.WARNING,
                    () -> "StringCredentials with id `" + headerValueId
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
                OTEL_EXPORTER_OTLP_HEADERS.asProperty(),
                this.getHeaderName() + "=" + this.getAuthenticationHeaderValue());
    }

    /**
     * Adds custom header authentication settings to OTel environment variables.
     *
     * @param environmentVariables mutable map of environment variables passed to OTel SDK initialization
     */
    @Override
    public void enrichOtelEnvironmentVariables(Map<String, String> environmentVariables) {
        // TODO don't overwrite 'otel.exporter.otlp.headers' if already defined, just append to it
        environmentVariables.put(
                OTEL_EXPORTER_OTLP_HEADERS.asEnvVar(),
                this.getHeaderName() + "=" + this.getAuthenticationHeaderValue());
    }

    /**
     * Returns the HTTP header name used for authentication.
     *
     * @return header name
     */
    public String getHeaderName() {
        return headerName;
    }

    /**
     * Sets the HTTP header name used for authentication.
     *
     * @param headerName header name
     */
    @DataBoundSetter
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    /**
     * Returns the Jenkins credentials ID providing header value.
     *
     * @return credentials ID
     */
    public String getHeaderValueId() {
        return headerValueId;
    }

    /**
     * Sets the Jenkins credentials ID providing header value.
     *
     * @param headerValueId credentials ID
     */
    @DataBoundSetter
    public void setHeaderValueId(String headerValueId) {
        this.headerValueId = headerValueId;
    }

    //    public ListBoxModel doFillHeaderValueIdItems() {
    //        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
    //            return new StandardListBoxModel().includeCurrentValue(this.headerValueId);
    //        }
    //        return new StandardListBoxModel()
    //                .includeEmptyValue()
    //                .includeMatchingAs(
    //                        ACL.SYSTEM,
    //                        Jenkins.get(),
    //                        StringCredentials.class,
    //                        Collections.<DomainRequirement>emptyList(),
    //                        CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StringCredentials.class)))
    //                .includeCurrentValue(headerValueId);
    //    }

    /**
     * Returns a debug-friendly representation of this authentication.
     *
     * @return textual representation
     */
    @Override
    public String toString() {
        return "OtlpHeaderAuthentication{" + "headerName='"
                + headerName + '\'' + ", headerValueId='"
                + headerValueId + '\'' + '}';
    }

    /**
     * Compares header authentication objects by header name and credential ID.
     *
     * @param o object to compare
     * @return {@code true} when fields match
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HeaderAuthentication that = (HeaderAuthentication) o;
        return Objects.equals(headerName, that.headerName) && Objects.equals(headerValueId, that.headerValueId);
    }

    /**
     * Returns hash code consistent with {@link #equals(Object)}.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(headerName, headerValueId);
    }

    @Extension
    @Symbol("otlpHeaderAuthentication")
    /** Descriptor for header-authentication form binding and UI integration. */
    public static class DescriptorImpl extends AbstractDescriptor {
        /**
         * Returns the descriptor display name shown in Jenkins UI.
         *
         * @return display name
         */
        @Override
        public String getDisplayName() {
            return "Header Authentication";
        }

        /**
         * Populates credential IDs for header value selection in descriptor forms.
         *
         * @return list-box model of available string credentials
         */
        public ListBoxModel doFillHeaderValueIdItems() {
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
