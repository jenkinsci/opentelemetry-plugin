/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import io.opentelemetry.api.common.AttributeType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

@Extension
public class WithSpanAttributeStep extends Step {
    private static final Logger logger = Logger.getLogger(WithSpanAttributeStep.class.getName());

    String key;
    Object value;
    AttributeType type;

    SpanAttributeTarget target;

    @DataBoundConstructor
    public WithSpanAttributeStep() {}

    @Override
    public StepExecution start(StepContext context) throws Exception {
        if (value == null) {
            // null attributes are NOT supported
            return new StepExecution(context) {
                @Override
                public boolean start() {
                    getContext()
                            .onFailure(new IllegalArgumentException(
                                    "withSpanAttribute requires the value parameter for key " + key));
                    return true;
                }
            };
        }
        AttributeType type = this.type;
        if (type == null) {
            boolean isArray = value.getClass().isArray();

            if (value instanceof Boolean) {
                type = isArray ? AttributeType.BOOLEAN_ARRAY : AttributeType.BOOLEAN;
            } else if (value instanceof Double || value instanceof Float) {
                type = isArray ? AttributeType.DOUBLE_ARRAY : AttributeType.DOUBLE;
            } else if (value instanceof Long || value instanceof Integer) {
                type = isArray ? AttributeType.LONG_ARRAY : AttributeType.LONG;
            } else {
                type = isArray ? AttributeType.STRING_ARRAY : AttributeType.STRING;
            }
        }

        return new SpanAttributeStepExecution(
                List.of(new SpanAttribute(key, value, type, target)), context.hasBody(), context);
    }

    public String getKey() {
        return key;
    }

    @DataBoundSetter
    public void setKey(String key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    @DataBoundSetter
    public void setValue(Object value) {
        this.value = value;
    }

    @CheckForNull
    public String getType() {
        return Optional.ofNullable(type).map(AttributeType::name).orElse(null);
    }

    /**
     * @param type case-insensitive representation of {@link AttributeType}
     */
    @DataBoundSetter
    public void setType(String type) {
        this.type = Optional.ofNullable(type)
                .map(String::trim)
                .filter(Predicate.not(String::isEmpty))
                .map(String::toUpperCase)
                .map(AttributeType::valueOf)
                .orElse(null);
    }

    /**
     * @param target case-insensitive representation of {@link SpanAttributeTarget}
     */
    @DataBoundSetter
    public void setTarget(String target) {
        this.target = Optional.ofNullable(target)
                .map(String::trim)
                .filter(Predicate.not(String::isEmpty))
                .map(String::toUpperCase)
                .map(SpanAttributeTarget::valueOf)
                .orElse(null);
    }

    @CheckForNull
    public String getTarget() {
        return Optional.ofNullable(target).map(SpanAttributeTarget::name).orElse(null);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        public static final String FUNCTION_NAME = "withSpanAttribute";

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
            return "Set Span Attribute";
        }

        public ListBoxModel doFillTypeItems(@AncestorInPath Item item, @AncestorInPath ItemGroup context) {
            List<AttributeType> supportedAttributeTypes = Arrays.asList(
                    AttributeType.STRING, AttributeType.LONG, AttributeType.BOOLEAN, AttributeType.DOUBLE);
            return new ListBoxModel(supportedAttributeTypes.stream()
                    .map(t -> new ListBoxModel.Option(t.name(), t.name()))
                    .collect(Collectors.toList()));
        }

        public ListBoxModel doFillTargetItems(@AncestorInPath Item item, @AncestorInPath ItemGroup context) {
            return new ListBoxModel(Arrays.stream(SpanAttributeTarget.values())
                    .map(t -> new ListBoxModel.Option(t.name(), t.name()))
                    .collect(Collectors.toList()));
        }
    }
}
