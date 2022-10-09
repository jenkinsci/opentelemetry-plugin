/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.workflow.steps;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Extension
public class WithSpanAttribute extends Step {
    private final static Logger logger = Logger.getLogger(WithSpanAttribute.class.getName());

    String key;
    Object value;
    AttributeType type;

    @DataBoundConstructor
    public WithSpanAttribute() {

    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        AttributeType type = this.type;
        if (type == null) {
            if (value instanceof Boolean) {
                type = AttributeType.BOOLEAN;
            } else if (value instanceof Double) {
                type = AttributeType.BOOLEAN;
            } else if (value instanceof Long || value instanceof Integer) {
                type = AttributeType.LONG;
            } else {
                type = AttributeType.STRING;
            }
        }
        return new Execution(key, value, type, context);
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

    public String getType() {
        return Optional.ofNullable(type).map(AttributeType::name).orElse(null);
    }

    @DataBoundSetter
    public void setType(String type) {
        this.type = Optional.ofNullable(type)
            .map(String::trim)
            .filter(OtelUtils.Predicates.not(String::isEmpty))
            .map(String::toUpperCase)
            .map(s -> AttributeType.valueOf(s)).orElse(null);
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
    }

    public static class Execution extends SynchronousStepExecution<Void> {

        @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
        private transient final String key;

        @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
        private transient final Object value;

        @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
        private transient final AttributeType attributeType;

        Execution(String key, Object value, AttributeType attributeType, StepContext context) {
            super(context);
            this.key = key;
            this.value = value;
            this.attributeType = attributeType;
        }

        @Override
        protected Void run() throws Exception {

            OtelTraceService otelTraceService = ExtensionList.lookupSingleton(OtelTraceService.class);
            Run run = getContext().get(Run.class);
            FlowNode flowNode = getContext().get(FlowNode.class);
            Span span = otelTraceService.getSpan(run, flowNode);
            Object convertedValue;
            AttributeKey attributeKey;
            switch (attributeType) {
                case BOOLEAN:
                    attributeKey = AttributeKey.booleanKey(key);
                    convertedValue = value instanceof Boolean ? value : Boolean.valueOf(value.toString());
                    break;
                case DOUBLE:
                    attributeKey = AttributeKey.doubleKey(key);
                    convertedValue = value instanceof Number ? ((Number) value).doubleValue() : Double.valueOf(value.toString());
                    break;
                case STRING:
                    attributeKey = AttributeKey.stringKey(key);
                    convertedValue = value instanceof String ? value : String.valueOf(value);
                    break;
                case LONG:
                    attributeKey = AttributeKey.longKey(key);
                    convertedValue = value instanceof Number ? ((Number) value).longValue() : Long.valueOf(value.toString());
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported span attribute type '" + attributeType + "'. " +
                        "Expected: " + Arrays.asList(AttributeType.BOOLEAN, AttributeType.DOUBLE, AttributeType.LONG, AttributeType.STRING));
            }
            logger.log(Level.FINE, () -> "setSpanAttribute: run=\"" + run.getParent().getName() + "#" + run.getId() + "\", key=" + key + " value=\"" + value + "\" type=" + attributeType);
            span.setAttribute(attributeKey, convertedValue);

            return null;
        }

        private static final long serialVersionUID = 1L;
    }
}
