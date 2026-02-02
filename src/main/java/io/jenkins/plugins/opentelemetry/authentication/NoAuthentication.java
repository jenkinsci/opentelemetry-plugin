/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.authentication;

import hudson.Extension;
import java.util.Map;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

@Extension
public class NoAuthentication extends OtlpAuthentication {

    @DataBoundConstructor
    public NoAuthentication() {}

    @Override
    public void enrichOpenTelemetryAutoConfigureConfigProperties(Map<String, String> configProperties) {}

    @Override
    public void enrichOtelEnvironmentVariables(Map<String, String> environmentVariables) {}

    @Override
    public String toString() {
        return "NoAuthentication{}";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NoAuthentication;
    }

    @Override
    public int hashCode() {
        return NoAuthentication.class.hashCode();
    }

    @Extension
    @Symbol("noAuthentication")
    public static class DescriptorImpl extends AbstractDescriptor {
        @Override
        public String getDisplayName() {
            return "No Authentication";
        }
    }
}
