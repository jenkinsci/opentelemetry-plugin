/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.steps;

import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class CreateSpanStep extends Step {

    /**
     * Attributes
     * The format is {@code attribute=value}.
     */
    private final List<String> attributes;

    @DataBoundConstructor
    public CreateSpanStep(List<String> attributes) {
        for (String pair : attributes) {
            if (pair.indexOf('=') == -1) {
                throw new IllegalArgumentException(pair);
            }
        }
        this.attributes = new ArrayList<>(attributes);
    }

    public List<String> getAttributes() {
        return attributes;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new CreateSpanExecution(attributes, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "createSpan";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Create a Span";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }

    }

}
