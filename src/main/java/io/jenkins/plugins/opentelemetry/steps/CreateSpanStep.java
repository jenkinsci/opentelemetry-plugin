/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.steps;

import hudson.Extension;
import hudson.model.TaskListener;
import io.jenkins.plugins.opentelemetry.steps.model.SpanAttribute;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CreateSpanStep extends Step {

    private final String name;
    private final List<SpanAttribute> attributes;

    @DataBoundConstructor
    public CreateSpanStep(String name, List<SpanAttribute> attributes) {
        this.name = name;
        this.attributes = attributes;
    }

    public CreateSpanStep(String name, Map<String, String> attributeMap) {
        this.name = name;
        this.attributes = transformMap(attributeMap);
    }

    private List<SpanAttribute> transformMap(Map<String, String> attributeMap) {
        List <SpanAttribute> list = new java.util.ArrayList<>(Collections.emptyList());
        attributeMap.forEach((key, value) -> { list.add(new SpanAttribute(key, value)); });
        return list;
    }

    public List<SpanAttribute> getAttributes() {
        return attributes;
    }
    public String getName() {
		return name;
	}

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new CreateSpanExecution(name, attributes, context);
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
