package io.jenkins.plugins.opentelemetry.trace;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.pipeline.listener.AbstractPipelineListener;
import io.jenkins.plugins.opentelemetry.pipeline.listener.PipelineStepListener;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadableSpan;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class TracingPipelineStepListener extends AbstractPipelineListener implements PipelineStepListener {
    private final static Logger LOGGER = Logger.getLogger(TracingPipelineStepListener.class.getName());

    private OtelTraceService otelTraceService;
    private Tracer tracer;

    @Override
    public void onBeforeStartStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        Span stageSpan = getTracer().spanBuilder("Stage: " + stageName)
                .setParent(Context.current())
                .setAttribute("jenkins.pipeline.step.type", "stage")
                .startSpan();
        LOGGER.log(Level.INFO, () -> "stage(" + stageName + ") { - start span '" + ((ReadableSpan) stageSpan).getName() + "' - " + ((ReadableSpan) stageSpan).toSpanData().getSpanId());

        getTracerService().putSpan(run, stageSpan);
    }

    @Override
    public void onAfterEndStageStep(@Nonnull StepEndNode stageStepEndNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        Span stageSpan = getTracerService().getSpan(run);
        LOGGER.log(Level.INFO, () -> "} // stage(" + stageName + ") - end span '" + ((ReadableSpan) stageSpan).getName() + "' - " + ((ReadableSpan) stageSpan).toSpanData().getSpanId());
        ErrorAction error = stageStepEndNode.getError();
        if (error != null) {
            stageSpan.recordException(error.getError());
        }
        stageSpan.end();
        getTracerService().removeSpan(run, stageSpan);
    }

    @Override
    public void onBeforeAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {
        Span atomicStepSpan = getTracer().spanBuilder(node.getDisplayFunctionName())
                .setParent(Context.current())
                .setAttribute("jenkins.pipeline.step.type", node.getDisplayFunctionName())
                .startSpan();
        LOGGER.log(Level.INFO, () -> node.getDisplayFunctionName() + "() > start span '" + ((ReadableSpan) atomicStepSpan).getName() + "' - " + ((ReadableSpan) atomicStepSpan).toSpanData().getSpanId());

        getTracerService().putSpan(run, atomicStepSpan);
    }

    @Override
    public void onAfterAtomicStep(@Nonnull StepAtomNode stepAtomNode, @Nonnull WorkflowRun run) {
        Span atomicStepSpan = getTracerService().getSpan(run);
        ErrorAction error = stepAtomNode.getError();
        if (error != null) {
            atomicStepSpan.recordException(error.getError());
        }
        atomicStepSpan.end();
        LOGGER.log(Level.INFO, () -> stepAtomNode.getDisplayFunctionName() + "() < end span '" + ((ReadableSpan) atomicStepSpan).getName() + "' - " + ((ReadableSpan) atomicStepSpan).toSpanData().getSpanId());
        getTracerService().removeSpan(run, atomicStepSpan);
    }

    @Inject
    public final void setOpenTelemetryTracerService(@Nonnull OtelTraceService otelTraceService) {
        this.otelTraceService = otelTraceService;
        this.tracer = this.otelTraceService.getTracer();
    }

    @Nonnull
    public OtelTraceService getTracerService() {
        return otelTraceService;
    }

    @Nonnull
    public Tracer getTracer() {
        return tracer;
    }
}
