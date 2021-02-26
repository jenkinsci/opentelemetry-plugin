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

import javax.annotation.Nonnull;

public abstract class OtlpAuthentication implements Describable<OtlpAuthentication>, ExtensionPoint {
    public abstract void configure(@Nonnull OtlpGrpcMetricExporterBuilder metricExporterBuilder);

    public abstract void configure(@Nonnull OtlpGrpcSpanExporterBuilder spanExporterBuilder);

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
