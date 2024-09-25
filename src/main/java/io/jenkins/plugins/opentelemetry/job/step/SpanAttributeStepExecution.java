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
import io.opentelemetry.api.trace.Span;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.GeneralNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpanAttributeStepExecution extends GeneralNonBlockingStepExecution {

    private static final Logger logger = Logger.getLogger(SpanAttributeStepExecution.class.getName());

    private final List<SpanAttribute> spanAttributes;

    private final boolean setOnChildren;

    private final boolean setAttributesOnlyOnParent;

    public SpanAttributeStepExecution(List<SpanAttribute> spanAttributes, boolean setOnChildren, StepContext context) {
        super(context);
        this.spanAttributes = spanAttributes;
        this.setOnChildren = setOnChildren;
        this.setAttributesOnlyOnParent = false;
    }

    public SpanAttributeStepExecution(List<SpanAttribute> spanAttributes, boolean setOnChildren, StepContext context, boolean setAttributesOnlyOnParent) {
        super(context);
        this.spanAttributes = spanAttributes;
        this.setOnChildren = setOnChildren;
        this.setAttributesOnlyOnParent = setAttributesOnlyOnParent;
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
        String currentSpanId = otelTraceService.getSpan(run, flowNode).getSpanContext().getSpanId();
        spanAttributes.forEach(spanAttribute -> {
            switch (spanAttribute.getTarget()) {
                case PIPELINE_ROOT_SPAN:
                    spanAttribute.setTargetSpan(otelTraceService.getPipelineRootSpan(run));
                    break;
                case CURRENT_SPAN:
                    spanAttribute.setTargetSpan(otelTraceService.getSpan(run, flowNode));
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported target span '" + spanAttribute.getTarget() + "'. ");
            }
        });

        spanAttributes.forEach(SpanAttribute::convert);

        spanAttributes.forEach(spanAttribute -> {
            logger.log(Level.FINE, () -> "spanAttribute: run=\"" + run.getParent().getName() + "#" + run.getId() + "\", key=" + spanAttribute.getKey() + " value=\"" + spanAttribute.getValue() + "\" type=" + spanAttribute.getAttributeType());
            spanAttribute.getTargetSpan().setAttribute(spanAttribute.getAttributeKey(), spanAttribute.getConvertedValue());
        });
        if (setOnChildren) {
            spanAttributes.forEach(spanAttribute -> {
                switch (spanAttribute.getTarget()) {
                    // We use Best Effort to set the attribute on existing child spans.
                    // Some child spans that were created before the execution of withSpanAttributes might be missed.
                    // Ideally we would set the attribute on all child spans that are still in progress and log a warning
                    // for closed child spans. (We cannot change attributes on a span that is closed, as it might already have been sent out.)
                    // Child spans created after the execution of withSpanAttributes will all have the attribute set correctly.
                    case PIPELINE_ROOT_SPAN:
                        Span phaseSpan = otelTraceService.getSpan(run);
                        phaseSpan.setAttribute(spanAttribute.getAttributeKey(), spanAttribute.getConvertedValue());
                        Span currentSpan = otelTraceService.getSpan(run, flowNode);
                        currentSpan.setAttribute(spanAttribute.getAttributeKey(), spanAttribute.getConvertedValue());
                        addAttributeToRunAction(run, spanAttribute.getAttributeKey(), spanAttribute.getConvertedValue());
                        break;
                    case CURRENT_SPAN:
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported target span '" + spanAttribute.getTarget() + "'. ");
                }
            });
            getContext()
                .newBodyInvoker()
                .withContext(mergeAttributes(getContext(), spanAttributes, currentSpanId))
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

    private OpenTelemetryAttributesAction mergeAttributes(StepContext context, List<SpanAttribute> spanAttributes, String currentSpanId) throws IOException, InterruptedException {
        OpenTelemetryAttributesAction existingAttributes = context.get(OpenTelemetryAttributesAction.class);
        OpenTelemetryAttributesAction resultingAttributes = new OpenTelemetryAttributesAction();
        if (existingAttributes != null) {
            resultingAttributes.getAttributes().putAll(existingAttributes.getAttributes());
        }
        spanAttributes.forEach(spanAttribute ->
            resultingAttributes.getAttributes().put(spanAttribute.getAttributeKey(), spanAttribute.getConvertedValue())
        );

        if (setAttributesOnlyOnParent) {
            // If the flag is set to true, then only the current span will get the attributes.
            // This will prevent any children from inheriting the attributes of the parent span.
            resultingAttributes.addSpanIdToInheritanceAllowedList(currentSpanId);
        }
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
