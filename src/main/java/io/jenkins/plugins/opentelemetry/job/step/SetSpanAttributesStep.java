/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import io.opentelemetry.api.common.AttributeType;

public class SetSpanAttributesStep extends Step {
    List<SpanAttribute> spanAttributes;

    @DataBoundConstructor
    public SetSpanAttributesStep(List<SpanAttribute> spanAttributes) {
        this.spanAttributes = spanAttributes;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        if (spanAttributes == null) {
            return new StepExecution(context) {
                @Override
                public boolean start() {
                    getContext().onFailure(new IllegalArgumentException("setSpanAttributes requires the spanAttributes parameter"));
                    return true;
                }
            };
        }
        List<SpanAttribute> noValueSet = spanAttributes.stream().filter(spanAttribute -> spanAttribute.getValue() == null).collect(Collectors.toList());
        if (!noValueSet.isEmpty()) {
            String keys = noValueSet.stream().map(SpanAttribute::getKey).reduce("", (accumulator, spanAttribute) -> {
                if (accumulator.isEmpty()) {
                    return spanAttribute;
                }
                return accumulator + ", " + spanAttribute;
            });
            // null attributes are NOT supported, log an error
            return new StepExecution(context) {
                @Override
                public boolean start() {
                    getContext().onFailure(new IllegalArgumentException("setSpanAttributes requires that all spanAttributes have a value set. The attribute(s) with the following keys violate this requirement: " + keys));
                    return true;
                }
            };
        }
        spanAttributes.forEach(SpanAttribute::setDefaultType);
        return new SpanAttributeStepExecution(spanAttributes, context.hasBody(), context);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        public static final String FUNCTION_NAME = "setSpanAttributes";

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }

        @Override
        public String getFunctionName() {
            return FUNCTION_NAME;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Set Span Attributes";
        }

        public ListBoxModel doFillTypeItems(@AncestorInPath Item item, @AncestorInPath ItemGroup context) {
            List<AttributeType> supportedAttributeTypes = Arrays.asList(AttributeType.STRING, AttributeType.LONG, AttributeType.BOOLEAN, AttributeType.DOUBLE);
            return new ListBoxModel(supportedAttributeTypes.stream().map(t -> new ListBoxModel.Option(t.name(), t.name())).collect(Collectors.toList()));
        }

        public ListBoxModel doFillTargetItems(@AncestorInPath Item item, @AncestorInPath ItemGroup context) {
            return new ListBoxModel(Arrays.stream(SpanAttributeTarget.values()).map(t -> new ListBoxModel.Option(t.name(), t.name())).collect(Collectors.toList()));
        }

    }

}
