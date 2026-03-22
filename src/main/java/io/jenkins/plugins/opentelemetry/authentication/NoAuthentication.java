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

    /**
     * Leaves OpenTelemetry properties unchanged because no authentication is used.
     *
     * @param configProperties mutable OpenTelemetry properties map
     */
    @Override
    public void enrichOpenTelemetryAutoConfigureConfigProperties(Map<String, String> configProperties) {}

    /**
     * Leaves OTEL environment variables unchanged because no authentication is used.
     *
     * @param environmentVariables mutable environment variables map
     */
    @Override
    public void enrichOtelEnvironmentVariables(Map<String, String> environmentVariables) {}

    /**
     * Returns a debug-friendly representation.
     *
     * @return textual representation
     */
    @Override
    public String toString() {
        return "NoAuthentication{}";
    }

    /**
     * Returns whether the given object is a {@link NoAuthentication} instance.
     *
     * @param o object to compare
     * @return {@code true} when the object is {@link NoAuthentication}
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof NoAuthentication;
    }

    /**
     * Returns hash code consistent with {@link #equals(Object)}.
     *
     * @return class-based hash code
     */
    @Override
    public int hashCode() {
        return NoAuthentication.class.hashCode();
    }

    @Extension
    @Symbol("noAuthentication")
    public static class DescriptorImpl extends AbstractDescriptor {
        /**
         * Returns the display name shown in Jenkins UI.
         *
         * @return descriptor display name
         */
        @Override
        public String getDisplayName() {
            return "No Authentication";
        }
    }
}
