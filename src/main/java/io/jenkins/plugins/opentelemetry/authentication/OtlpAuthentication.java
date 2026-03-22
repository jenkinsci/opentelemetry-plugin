/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.authentication;

import com.google.common.collect.ComparisonChain;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.util.Map;
import jenkins.model.Jenkins;

/**
 * Base extension point for OTLP authentication strategies.
 */
public abstract class OtlpAuthentication implements Describable<OtlpAuthentication>, ExtensionPoint {
    /**
     * Enriches OpenTelemetry autoconfiguration properties with authentication settings.
     *
     * @param configProperties mutable OpenTelemetry properties map
     */
    public abstract void enrichOpenTelemetryAutoConfigureConfigProperties(Map<String, String> configProperties);

    /**
     * Enrich the provided environment variables injecting the authentication settings,
     * typically appending credentials to the {@code OTEL_EXPORTER_OTLP_HEADERS} variable
     * @param environmentVariables the builder to configure
     */
    public abstract void enrichOtelEnvironmentVariables(@NonNull Map<String, String> environmentVariables);

    /**
     * Returns the Jenkins descriptor for this authentication implementation.
     *
     * @return descriptor for this authentication type
     */
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

    /**
     * Descriptor base type for OTLP authentication implementations.
     */
    public abstract static class AbstractDescriptor extends Descriptor<OtlpAuthentication>
            implements Comparable<AbstractDescriptor> {
        /**
         * Override alpha sorting
         * @return ordinal position
         */
        public int ordinal() {
            return 0;
        }

        /**
         * Compares descriptors by ordinal then display name.
         *
         * @param o the other descriptor
         * @return comparison result for deterministic ordering
         */
        @Override
        public int compareTo(OtlpAuthentication.AbstractDescriptor o) {
            return ComparisonChain.start()
                    .compare(this.ordinal(), o.ordinal())
                    .compare(this.getDisplayName(), o.getDisplayName())
                    .result();
        }
    }
}
