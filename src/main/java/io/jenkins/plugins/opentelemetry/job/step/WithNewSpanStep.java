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
import org.kohsuke.stapler.DataBoundSetter;

public class WithNewSpanStep extends Step {

    private final String label;
    private List<SpanAttribute> attributes = new ArrayList<>();
    private boolean setAttributesOnlyOnParent = false;

    /**
     * Creates a withNewSpan step with the given span label.
     *
     * @param label span label
     */
    @DataBoundConstructor
    public WithNewSpanStep(String label) {
        this.label = label;
    }

    /**
     * Returns span label.
     *
     * @return span label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns span attributes to set.
     *
     * @return list of span attributes
     */
    public List<SpanAttribute> getAttributes() {
        return attributes;
    }

    /**
     * Returns whether attributes are set only on the parent span.
     *
     * @return {@code true} when attributes are set on the parent span only
     */
    public boolean isSetAttributesOnlyOnParent() {
        return setAttributesOnlyOnParent;
    }

    /**
     * Sets span attributes.
     *
     * @param attributes list of span attributes; {@code null} clears the list
     */
    @DataBoundSetter
    public void setAttributes(List<SpanAttribute> attributes) {
        // Allow empty attributes.
        this.attributes = attributes != null ? attributes : new ArrayList<>();
    }

    /**
     * Sets whether attributes are applied only to the parent span.
     *
     * @param setAttributesOnlyOnParent {@code true} to apply only to the parent span; {@code null} treated as {@code false}
     */
    @DataBoundSetter
    public void setSetAttributesOnlyOnParent(Boolean setAttributesOnlyOnParent) {
        // Set to 'false', if no value is provided.
        this.setAttributesOnlyOnParent = setAttributesOnlyOnParent != null && setAttributesOnlyOnParent;
    }

    /**
     * Returns the descriptor for this step.
     *
     * @return step descriptor
     */
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Starts execution of this step.
     *
     * @param context step execution context
     * @return step execution
     * @throws Exception if execution cannot be started
     */
    @Override
    public StepExecution start(StepContext context) throws Exception {
        // Set AttributeType for any provided attributes, to avoid an exception if null.
        attributes.forEach(SpanAttribute::setDefaultType);

        return new SpanAttributeStepExecution(attributes, context.hasBody(), context, setAttributesOnlyOnParent);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        /**
         * Returns Pipeline DSL function name for this step.
         *
         * @return DSL function name
         */
        @Override
        public String getFunctionName() {
            return "withNewSpan";
        }

        /**
         * Returns whether this step takes a block body.
         *
         * @return {@code true} as withNewSpan wraps a block
         */
        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        /**
         * Returns display name used in Pipeline snippet generator.
         *
         * @return step display name
         */
        @Override
        public String getDisplayName() {
            return "Step with a new user-defined Span";
        }

        /**
         * Returns required context types for this step.
         *
         * @return required context set
         */
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }
    }
}
