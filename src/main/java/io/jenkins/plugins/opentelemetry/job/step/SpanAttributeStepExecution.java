/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import hudson.ExtensionList;
import hudson.model.Actionable;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.OpenTelemetryAttributesAction;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.trace.Span;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.GeneralNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpanAttributeStepExecution extends GeneralNonBlockingStepExecution {

    private final static Logger logger = Logger.getLogger(SpanAttributeStepExecution.class.getName());

    private final String key;

    private final Object value;

    private final AttributeType attributeType;

    private final SpanAttributeTarget target;

    private final boolean setOnChildren;

    public SpanAttributeStepExecution(String key, Object value, AttributeType attributeType, SpanAttributeTarget target, boolean setOnChildren, StepContext context) {
        super(context);
        this.key = key;
        this.value = value;
        this.attributeType = attributeType;
        this.target = target;
        this.setOnChildren = setOnChildren;
    }

    @Override
    public boolean start() throws Exception {
        if (setOnChildren) {
            try {
                run();
            } catch (Throwable t) {
                getContext().onFailure(t);
            }
            return false;
        }
        try {
            getContext().onSuccess(run());
        } catch (Throwable t) {
            getContext().onFailure(t);
        }
        return true;
    }

    protected Void run() throws Exception {
        OtelTraceService otelTraceService = ExtensionList.lookupSingleton(OtelTraceService.class);
        Run run = getContext().get(Run.class);
        FlowNode flowNode = getContext().get(FlowNode.class);
        final Span span;
        switch (target) {
            case PIPELINE_ROOT_SPAN:
                span = otelTraceService.getPipelineRootSpan(run);
                break;
            case CURRENT_SPAN:
                span = otelTraceService.getSpan(run, flowNode);
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
        if (setOnChildren) {
            switch (target) {
                // We use Best Effort to set the attribute on existing child spans.
                // Some child spans that were created before the execution of withSpanAttribute might be missed.
                // Ideally we would set the attribute on all child spans that are still in progress and log a warning
                // for closed child spans. (We cannot change attributes on a span that is closed, as it might already have been sent out.)
                // Child spans created after the execution of withSpanAttribute will all have the attribute set correctly.
                case PIPELINE_ROOT_SPAN:
                    Span phaseSpan = otelTraceService.getSpan(run);
                    phaseSpan.setAttribute(attributeKey, convertedValue);
                    Span currentSpan = otelTraceService.getSpan(run, flowNode);
                    currentSpan.setAttribute(attributeKey, convertedValue);
                    addAttributeToRunAction(run, attributeKey, convertedValue);
                    break;
                case CURRENT_SPAN:
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported target span '" + target + "'. ");
            }
            getContext()
                .newBodyInvoker()
                .withContext(mergeAttribute(getContext(), attributeKey, convertedValue))
                .withCallback(NopCallback.INSTANCE)
                .start();
        }

        return null;
    }

    private void addAttributeToRunAction(Actionable actionable, AttributeKey attributeKey, Object convertedValue) {
        OpenTelemetryAttributesAction openTelemetryAttributesAction = actionable.getAction(OpenTelemetryAttributesAction.class);
        if (openTelemetryAttributesAction == null) {
            actionable.addAction(new OpenTelemetryAttributesAction());
            openTelemetryAttributesAction = actionable.getAction(OpenTelemetryAttributesAction.class);
        }
        openTelemetryAttributesAction.getAttributes().put(attributeKey, convertedValue);
    }

    private OpenTelemetryAttributesAction mergeAttribute(StepContext context, AttributeKey attributeKey, Object convertedValue) throws IOException, InterruptedException {
        OpenTelemetryAttributesAction existingAttributes = context.get(OpenTelemetryAttributesAction.class);
        OpenTelemetryAttributesAction resultingAttributes = new OpenTelemetryAttributesAction();
        if (existingAttributes != null) {
            resultingAttributes.getAttributes().putAll(existingAttributes.getAttributes());
        }
        resultingAttributes.getAttributes().put(attributeKey, convertedValue);
        return resultingAttributes;
    }

    private static final long serialVersionUID = 1L;

    private static class NopCallback extends BodyExecutionCallback.TailCall {

        private static final long serialVersionUID = 957579239256583870L;

        public static final NopCallback INSTANCE = new NopCallback();

        @Override
        protected void finished(StepContext context) throws Exception {
            // no op
        }
    }

}
