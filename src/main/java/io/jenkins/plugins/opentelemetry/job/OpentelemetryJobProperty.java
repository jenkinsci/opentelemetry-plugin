/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;

public class OpentelemetryJobProperty extends JobProperty<Job<?,?>> {

    private final String metadata;

    @DataBoundConstructor
    public OpentelemetryJobProperty(@Nonnull String metadata) {
        this.metadata = metadata;
    }

    public String getMetadata() {
        return metadata;
    }

    @Extension
    @Symbol("opentelemetry")
    public static class DescriptorImpl extends JobPropertyDescriptor {
        @Override
        public String getDisplayName() {
            return "Add OpenTelemetry attributes to the job";
        }

        public String getPropertyName() {
            return "opentelemetry-property";
        }

        @Override
        public OpentelemetryJobProperty newInstance(StaplerRequest req, JSONObject formData)
            throws hudson.model.Descriptor.FormException {
            if (formData == null || formData.isNullObject()) {
                return null;
            }
            JSONObject form = formData.getJSONObject(getPropertyName());
            if (form == null || form.isNullObject()) {
                return null;
            }
            return (OpentelemetryJobProperty) super.newInstance(req, form);
        }
    }
}