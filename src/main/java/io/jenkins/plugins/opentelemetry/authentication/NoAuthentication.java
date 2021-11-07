/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.authentication;

import hudson.Extension;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.logging.Logger;

@Extension
public class NoAuthentication extends OtlpAuthentication {
    private final static Logger LOGGER = Logger.getLogger(NoAuthentication.class.getName());

    @DataBoundConstructor
    public NoAuthentication() {
    }

    @Override
    public void enrichOpenTelemetryAutoConfigureConfigProperties(Map<String, String> configProperties) {
    }

    @Override
    public void enrichOtelEnvironmentVariables(Map<String, String> environmentVariables) {
    }

    @Override
    public String toString() {
        return "NoAuthentication{}";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NoAuthentication)) {
            return false;
        }
        return true;
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
