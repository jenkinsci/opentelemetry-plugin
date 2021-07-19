/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.steps.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class SpanAttribute extends AbstractDescribableImpl<SpanAttribute> {

    private String key;
    private final String value;

    @DataBoundConstructor
    public SpanAttribute(@NonNull String key, @NonNull String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<SpanAttribute> {

        @Override
        public String getDisplayName() {
            return "Span attribute value pair";
        }
    }
}
