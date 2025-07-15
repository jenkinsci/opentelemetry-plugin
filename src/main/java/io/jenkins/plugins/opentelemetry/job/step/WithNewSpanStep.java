/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import hudson.Extension;
import hudson.model.TaskListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

public class WithNewSpanStep extends Step {

    private final String label;
    private final List<SpanAttribute> attributes;
    private final boolean setAttributesOnlyOnParent;

    @DataBoundConstructor
    public WithNewSpanStep(String label, List<SpanAttribute> attributes, Boolean setAttributesOnlyOnParent) {
        this.label = label;
        // Allow empty attributes.
        this.attributes = attributes == null ? new ArrayList<>() : attributes;
        // Set to 'false', if no value is provided.
        this.setAttributesOnlyOnParent = setAttributesOnlyOnParent != null && setAttributesOnlyOnParent;
    }

    public String getLabel() {
        return label;
    }

    public List<SpanAttribute> getAttributes() {
        return attributes;
    }

    public boolean isSetAttributesOnlyOnParent() {
        return setAttributesOnlyOnParent;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        // Set AttributeType for any provided attributes, to avoid an exception if null.
        attributes.forEach(SpanAttribute::setDefaultType);

        return new SpanAttributeStepExecution(attributes, context.hasBody(), context, setAttributesOnlyOnParent);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "withNewSpan";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Step with a new user-defined Span";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }
    }
}
