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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class HeaderAuthentication extends OtlpAuthentication {
    private final static Logger LOGGER = Logger.getLogger(HeaderAuthentication.class.getName());

    private String headerName;
    private String headerValueId;

    @DataBoundConstructor
    public HeaderAuthentication() {
    }

    private String getAuthenticationHeaderValue() {
        StringCredentials credentials = (StringCredentials) CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StringCredentials.class, Jenkins.get(),
                        ACL.SYSTEM, Collections.EMPTY_LIST),
                CredentialsMatchers.withId(this.headerValueId));
        String authenticationTokenValue;
        if (credentials == null) {
            // TODO better handling
            LOGGER.log(Level.WARNING, () -> "StringCredentials with id `" + headerValueId + "` not found. Fall back to empty secret, an authentication error is likely to happen.");
            authenticationTokenValue = "";
        } else {
            authenticationTokenValue = Secret.toString(credentials.getSecret());
        }
        return authenticationTokenValue;
    }

    @Override
    public void configure(OtlpGrpcMetricExporterBuilder metricExporterBuilder) {
        metricExporterBuilder.addHeader(this.getHeaderName(), this.getAuthenticationHeaderValue());
    }

    @Override
    public void configure(OtlpGrpcSpanExporterBuilder spanExporterBuilder) {
        spanExporterBuilder.addHeader(this.getHeaderName(), this.getAuthenticationHeaderValue());
    }

    @Override
    public void enrichOtelEnvironmentVariables(Map<String, String> environmentVariables) {
        // TODO don't overwrite OTEL_EXPORTER_OTLP_HEADERS if already defined, just append to it
        environmentVariables.put("OTEL_EXPORTER_OTLP_HEADERS", this.getHeaderName() + "=" + this.getAuthenticationHeaderValue());
    }
    public String getHeaderName() {
        return headerName;
    }

    @DataBoundSetter
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getHeaderValueId() {
        return headerValueId;
    }

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

    @Override
    public String toString() {
        return "OtlpHeaderAuthentication{" +
                "headerName='" + headerName + '\'' +
                ", headerValueId='" + headerValueId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HeaderAuthentication that = (HeaderAuthentication) o;
        return Objects.equals(headerName, that.headerName) && Objects.equals(headerValueId, that.headerValueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(headerName, headerValueId);
    }

    @Extension
    @Symbol("otlpHeaderAuthentication")
    public static class DescriptorImpl extends AbstractDescriptor {
        @Override
        public String getDisplayName() {
            return "Header Authentication";
        }

        public ListBoxModel doFillHeaderValueIdItems() {
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
