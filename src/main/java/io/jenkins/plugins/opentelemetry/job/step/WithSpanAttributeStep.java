/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.trace.Span;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Extension
public class WithSpanAttributeStep extends Step {
    private final static Logger logger = Logger.getLogger(WithSpanAttributeStep.class.getName());

    enum Target {CURRENT_SPAN, PIPELINE_ROOT_SPAN}

    String key;
    Object value;
    AttributeType type;

    Target target;

    @DataBoundConstructor
    public WithSpanAttributeStep() {

    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        if (value == null) {
            // null attributes are NOT supported
            // todo log message
            return new StepExecution(context) {
                @Override
                public boolean start() {
                    return false;
                }
            };
        }
        AttributeType type = this.type;
        if (type == null) {
            boolean isArray = value.getClass().isArray();

            if (value instanceof Boolean) {
                type = isArray ? AttributeType.BOOLEAN_ARRAY : AttributeType.BOOLEAN;
            } else if (value instanceof Double || value instanceof Float) {
                type = isArray ? AttributeType.DOUBLE_ARRAY: AttributeType.DOUBLE;
            } else if (value instanceof Long || value instanceof Integer) {
                type = isArray ? AttributeType.LONG_ARRAY : AttributeType.LONG;
            } else {
                type = isArray ? AttributeType.STRING_ARRAY : AttributeType.STRING;
            }
        }

        return new Execution(key, value, type, Objects.requireNonNullElse(target, Target.CURRENT_SPAN), context);
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
            .filter(OtelUtils.Predicates.not(String::isEmpty))
            .map(String::toUpperCase)
            .map(AttributeType::valueOf).orElse(null);
    }

    /**
     * @param target case-insensitive representation of {@link Target}
     */
    @DataBoundSetter
    public void setTarget(String target) {
        this.target = Optional.ofNullable(target)
            .map(String::trim)
            .filter(OtelUtils.Predicates.not(String::isEmpty))
            .map(String::toUpperCase)
            .map(Target::valueOf)
            .orElse(null);
    }

    @CheckForNull
    public String getTarget() {
        return Optional.ofNullable(target).map(Target::name).orElse(null);
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

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Set Span Attribute";
        }

        public ListBoxModel doFillTypeItems(@AncestorInPath Item item, @AncestorInPath ItemGroup context) {
            List<AttributeType> supportedAttributeTypes = Arrays.asList(AttributeType.STRING, AttributeType.LONG, AttributeType.BOOLEAN, AttributeType.DOUBLE);
            return new ListBoxModel(supportedAttributeTypes.stream().map(t -> new ListBoxModel.Option(t.name(), t.name())).collect(Collectors.toList()));
        }

        public ListBoxModel doFillTargetItems(@AncestorInPath Item item, @AncestorInPath ItemGroup context) {
            return new ListBoxModel(Arrays.stream(Target.values()).map(t -> new ListBoxModel.Option(t.name(), t.name())).collect(Collectors.toList()));
        }
    }

    public static class Execution extends SynchronousStepExecution<Void> {

        private final String key;

        private final Object value;

        private final AttributeType attributeType;

        private final Target target;

        Execution(String key, Object value, AttributeType attributeType, Target target, StepContext context) {
            super(context);
            this.key = key;
            this.value = value;
            this.attributeType = attributeType;
            this.target = target;
        }

        @Override
        protected Void run() throws Exception {
            OtelTraceService otelTraceService = ExtensionList.lookupSingleton(OtelTraceService.class);
            Run run = getContext().get(Run.class);
            FlowNode flowNode = getContext().get(FlowNode.class);
            final Span span;
            switch (target) {
                case PIPELINE_ROOT_SPAN:
                    span= otelTraceService.getPipelineRootSpan(run);
                    break;
                case CURRENT_SPAN:
                    span= otelTraceService.getSpan(run, flowNode);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported target span '" + target + "'. ");
            }
            Object convertedValue;
            AttributeKey attributeKey;
            switch (attributeType) {
                case BOOLEAN:
                    attributeKey = AttributeKey.booleanKey(key);
                    convertedValue = value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
                    break;
                case DOUBLE:
                    attributeKey = AttributeKey.doubleKey(key);
                    convertedValue = value instanceof Double ? value : value instanceof Float ? ((Float) value).doubleValue() : Double.parseDouble(value.toString());
                    break;
                case STRING:
                    attributeKey = AttributeKey.stringKey(key);
                    convertedValue = value instanceof String ? value : value.toString();
                    break;
                case LONG:
                    attributeKey = AttributeKey.longKey(key);
                    convertedValue = value instanceof Long ?  value : value instanceof Integer ?  ((Integer) value).longValue() : Long.parseLong(value.toString());
                    break;
                case BOOLEAN_ARRAY:
                    attributeKey = AttributeKey.booleanArrayKey(key);
                    convertedValue = value; // todo try to convert if needed
                    break;
                case DOUBLE_ARRAY:
                    attributeKey = AttributeKey.doubleArrayKey(key);
                    convertedValue = value; // todo try to convert if needed
                    break;
                case LONG_ARRAY:
                    attributeKey = AttributeKey.longArrayKey(key);
                    convertedValue = value; // todo try to convert if needed
                    break;
                case STRING_ARRAY:
                    attributeKey = AttributeKey.stringArrayKey(key);
                    convertedValue = value; // todo try to convert if needed
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported span attribute type '" + attributeType + "'. ");
            }
            logger.log(Level.FINE, () -> "setSpanAttribute: run=\"" + run.getParent().getName() + "#" + run.getId() + "\", key=" + key + " value=\"" + value + "\" type=" + attributeType);
            span.setAttribute(attributeKey, convertedValue);

            return null;
        }

        private static final long serialVersionUID = 1L;
    }
}
