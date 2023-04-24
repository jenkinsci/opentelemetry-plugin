/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.authentication;

import com.google.common.collect.ComparisonChain;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import jenkins.model.Jenkins;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

public abstract class OtlpAuthentication implements Describable<OtlpAuthentication>, ExtensionPoint {
    public abstract void enrichOpenTelemetryAutoConfigureConfigProperties(Map<String, String> configProperties);

    /**
     * Enrich the provided environment variables injecting the authentication settings,
     * typically appending credentials to the {@code OTEL_EXPORTER_OTLP_HEADERS} variable
     * @param environmentVariables the builder to configure
     */
    public abstract void enrichOtelEnvironmentVariables(@NonNull Map<String, String> environmentVariables);

    @Override
    public Descriptor<OtlpAuthentication> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * Returns all the registered {@link OtlpAuthentication} descriptors. Used by the GUI
     */
    public static DescriptorExtensionList<OtlpAuthentication, AbstractDescriptor> allDescriptors() {
        return Jenkins.get().getDescriptorList(OtlpAuthentication.class);
    }

    public static abstract class AbstractDescriptor extends Descriptor<OtlpAuthentication> implements Comparable<AbstractDescriptor> {
        /**
         * Override alpha sorting
         * @return ordinal position
         */
        public int ordinal() {
            return 0;
        }

        @Override
        public int compareTo(OtlpAuthentication.AbstractDescriptor o) {
            return ComparisonChain.start().compare(this.ordinal(), o.ordinal()).compare(this.getDisplayName(), o.getDisplayName()).result();
        }
    }
}
