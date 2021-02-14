/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import com.google.common.base.Strings;
import groovy.lang.Writable;
import groovy.text.GStringTemplateEngine;
import groovy.text.Template;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ObservabilityBackend implements Describable<ObservabilityBackend>, ExtensionPoint {
    private final static Logger LOGGER = Logger.getLogger(ObservabilityBackend.class.getName());

    private transient Template traceVisualisationUrlGTemplate;

    @CheckForNull
    public abstract String getTraceVisualisationUrlTemplate();

    @CheckForNull
    public abstract String getMetricsVisualisationUrlTemplate();

    @CheckForNull
    public abstract String getIconPath();

    @CheckForNull
    public abstract String getEnvVariable();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    public abstract Map<String, Object> mergeBindings(Map<String, Object> bindings);

    /**
     * @return {@code null} if no {@link #getMetricsVisualisationUrlTemplate()} has been defined or if the {@link #getMetricsVisualisationUrlTemplate} has a syntax error
     */
    @CheckForNull
    public String getTraceVisualisationUrl(Map<String, Object> bindings) {
        if (Strings.isNullOrEmpty(this.getTraceVisualisationUrlTemplate())) {
            return null;
        }
        if (this.traceVisualisationUrlGTemplate == null) {

            GStringTemplateEngine gStringTemplateEngine = new GStringTemplateEngine();
            try {
                this.traceVisualisationUrlGTemplate = gStringTemplateEngine.createTemplate(this.getTraceVisualisationUrlTemplate());
            } catch (IOException | ClassNotFoundException e) {
                LOGGER.log(Level.WARNING, "Invalid Trace Visualisation URL Template '" + this.getTraceVisualisationUrlTemplate() + "'", e);
                this.traceVisualisationUrlGTemplate = ERROR_TEMPLATE;
            }
        }
        if (traceVisualisationUrlGTemplate == ERROR_TEMPLATE ) {
            return null;
        }
        Map<String, Object> mergedBindings = mergeBindings(bindings);
        return traceVisualisationUrlGTemplate.make(mergedBindings).toString();
    }

    @Override
    public Descriptor<ObservabilityBackend> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * Returns all the registered {@link ObservabilityBackend} descriptors. Used by the GUI
     */
    public static DescriptorExtensionList<ObservabilityBackend, Descriptor<ObservabilityBackend>> allDescriptors() {
        return Jenkins.get().getDescriptorList(ObservabilityBackend.class);
    }

    public final static Template ERROR_TEMPLATE = new Template() {
        @Override
        public Writable make() {
            return out -> {
                out.write("#ERROR#");
                return out;
            };
        }

        @Override
        public Writable make(Map binding) {
            return out -> {
                out.write("#ERROR#");
                return out;
            };
        }
    };
}
