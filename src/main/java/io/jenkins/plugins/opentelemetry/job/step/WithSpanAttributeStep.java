/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.jenkins.plugins.opentelemetry.job.action.AttributeSetterAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.trace.Span;

@Extension
public class WithSpanAttributeStep extends Step {
    private final static Logger logger = Logger.getLogger(WithSpanAttributeStep.class.getName());

    enum Target {CURRENT_SPAN, PIPELINE_ROOT_SPAN}

    enum SetOn {TARGET_ONLY, TARGET_AND_CHILDREN}

    String key;
    Object value;
    AttributeType type;

    Target target;

    SetOn setOn;

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

        return new Execution(key, value, type, Objects.requireNonNullElse(target, Target.CURRENT_SPAN), Objects.requireNonNullElse(setOn, SetOn.TARGET_ONLY), context);
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
            .map(AttributeType::valueOf).orElse(null);
    }

    /**
     * @param target case-insensitive representation of {@link Target}
     */
    @DataBoundSetter
    public void setTarget(String target) {
        this.target = Optional.ofNullable(target)
            .map(String::trim)
            .filter(Predicate.not(String::isEmpty))
            .map(String::toUpperCase)
            .map(Target::valueOf)
            .orElse(null);
    }

    @CheckForNull
    public String getTarget() {
        return Optional.ofNullable(target).map(Target::name).orElse(null);
    }

    /**
     * @param setOn case-insensitive representation of {@link SetOn}
     */
    @DataBoundSetter
    public void setSetOn(String setOn) {
        this.setOn = Optional.ofNullable(setOn)
            .map(String::trim)
            .filter(Predicate.not(String::isEmpty))
            .map(String::toUpperCase)
            .map(SetOn::valueOf)
            .orElse(null);
    }

    @CheckForNull
    public String getSetOn() {
        return Optional.ofNullable(setOn).map(SetOn::name).orElse(null);
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

        private final SetOn setOn;

        Execution(String key, Object value, AttributeType attributeType, Target target, SetOn setOn, StepContext context) {
            super(context);
            this.key = key;
            this.value = value;
            this.attributeType = attributeType;
            this.target = target;
            this.setOn = setOn;
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
            AttributeSetterAction setAttribute = new AttributeSetterAction(run, key, value, attributeType);
            setAttribute.setToSpan(span);
            if (SetOn.TARGET_AND_CHILDREN.equals(setOn)) {
                switch (target) {
                    // We use Best Effort to set the attribute on existing child spans.
                    // Some child spans that were created before the execution of withSpanAttribute might be missed.
                    // Ideally we would set the attribute on all child spans that are still in progress and log a warning
                    // for closed child spans. (We cannot change attributes on a span that is closed, as it might already have been sent out.)
                    // Child spans created after the execution of withSpanAttribute will all have the attribute set correctly.
                    case PIPELINE_ROOT_SPAN:
                        Span phaseSpan= otelTraceService.getSpan(run);
                        setAttribute.setToSpan(phaseSpan);
                        Span currentSpan= otelTraceService.getSpan(run, flowNode);
                        setAttribute.setToSpan(currentSpan);
                        run.addAction(setAttribute);
                        break;
                    case CURRENT_SPAN:
                        getClosestBlockNode(flowNode).addAction(setAttribute);
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported target span '" + target + "'. ");
                }
            }

            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    private static FlowNode getClosestBlockNode(@NonNull final FlowNode flowNode) {
        if (flowNode instanceof StepEndNode) {
            return ((StepEndNode) flowNode).getStartNode();
        }
        if (flowNode instanceof StepStartNode) {
            return flowNode;
        }
        return flowNode.getEnclosingBlocks().get(0);
    }
}
