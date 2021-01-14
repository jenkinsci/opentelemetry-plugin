package io.jenkins.plugins.opentelemetry.trace;

import com.google.errorprone.annotations.MustBeClosed;
import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.pipeline.PipelineNodeUtil;
import io.jenkins.plugins.opentelemetry.pipeline.listener.AbstractPipelineListener;
import io.jenkins.plugins.opentelemetry.pipeline.listener.PipelineStepListener;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.ReadableSpan;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Verify.verifyNotNull;

@Extension
public class TracingPipelineStepListener extends AbstractPipelineListener implements PipelineStepListener {
    private final static Logger LOGGER = Logger.getLogger(TracingPipelineStepListener.class.getName());

    private OtelTraceService otelTraceService;
    private Tracer tracer;

    @Override
    public void onBeforeStartStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        try (Scope ignored = setupContext(stepStartNode)) {
            verifyNotNull(ignored, "No span found for node %s", stepStartNode);

            Span stageSpan = getTracer().spanBuilder("Stage: " + stageName)
                    .setParent(Context.current())
                    .setAttribute("jenkins.pipeline.step.type", "stage")
                    .startSpan();
            LOGGER.log(Level.INFO, () -> run.getFullDisplayName() + " - stage(" + stageName + ") - begin " + OtelUtils.toDebugString(stageSpan));

            getTracerService().putSpan(run, stageSpan);
        }
    }

    @Override
    public void onAfterEndStageStep(@Nonnull StepEndNode stageStepEndNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        try (Scope ignored = setupContext(stageStepEndNode)) {
            verifyNotNull(ignored, "No span found for node %s", stageStepEndNode);

            Span stageSpan = getTracerService().getSpan(run);
            LOGGER.log(Level.INFO, () -> run.getFullDisplayName() + " - // end stage(" + stageName + ") - begin " + OtelUtils.toDebugString(stageSpan));
            ErrorAction error = stageStepEndNode.getError();
            if (error != null) {
                stageSpan.recordException(error.getError());
            }
            stageSpan.end();
            getTracerService().removeSpan(run, stageSpan);
        }
    }

    @Override
    public void onBeforeAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {
        try (Scope ignored = setupContext(node)) {
            verifyNotNull(ignored, "No span found for node %s", node);

            Span atomicStepSpan = getTracer().spanBuilder(node.getDisplayFunctionName())
                    .setParent(Context.current())
                    .setAttribute("jenkins.pipeline.step.type", node.getDisplayFunctionName())
                    .startSpan();
            LOGGER.log(Level.INFO, () -> run.getFullDisplayName() + " - > " + node.getDisplayFunctionName() + " - begin " + OtelUtils.toDebugString(atomicStepSpan));

            getTracerService().putSpan(run, atomicStepSpan);
        }
    }

    @Override
    public void onAfterAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {
        try (Scope ignored = setupContext(node)) {
            verifyNotNull(ignored, "No span found for node %s", node);

            Span atomicStepSpan = getTracerService().getSpan(run);
            ErrorAction error = node.getError();
            if (error != null) {
                atomicStepSpan.recordException(error.getError());
            }
            atomicStepSpan.end();
            LOGGER.log(Level.INFO, () -> run.getFullDisplayName() + " - < " + node.getDisplayFunctionName() + " - end " + OtelUtils.toDebugString(atomicStepSpan));

            getTracerService().removeSpan(run, atomicStepSpan);
        }
    }

    /**
     * @return {@code null} if no {@link Span} has been created for the {@link Run} of the given {@link FlowNode}
     */
    @CheckForNull
    @MustBeClosed
    protected Scope setupContext(@Nonnull FlowNode node) {
        Run run = PipelineNodeUtil.getWorkflowRun(node);
        verifyNotNull(run, "No run found for node %s", node);
        return getTracerService().setupContext(run);
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
