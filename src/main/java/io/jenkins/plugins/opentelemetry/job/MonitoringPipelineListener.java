package io.jenkins.plugins.opentelemetry.job;

import static com.google.common.base.Verify.*;

import com.google.errorprone.annotations.MustBeClosed;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.OpenTelemetryAttributesAction;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.FlowNodeContextKey;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.RunContextKey;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.job.jenkins.AbstractPipelineListener;
import io.jenkins.plugins.opentelemetry.job.jenkins.PipelineListener;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.resources.ResourceAttributes;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;


@Extension
public class MonitoringPipelineListener extends AbstractPipelineListener implements PipelineListener, StepListener {
    private final static Logger LOGGER = Logger.getLogger(MonitoringPipelineListener.class.getName());

    private OtelTraceService otelTraceService;
    private Tracer tracer;

    @Override
    public void onStartStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        try (Scope ignored = setupContext(run, stepStartNode)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, stepStartNode);

            Span stageSpan = getTracer().spanBuilder("Stage: " + stageName)
                    .setParent(Context.current())
                    .setAttribute("jenkins.pipeline.step.type", stepStartNode.getDisplayFunctionName())
                    .startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - stage(" + stageName + ") - begin " + OtelUtils.toDebugString(stageSpan));

            getTracerService().putSpan(run, stageSpan, stepStartNode);
        }
    }

    @Override
    public void onEndStageStep(@Nonnull StepEndNode node, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        endCurrentSpan(node, run);
    }

    /**
     * TODO for steps like SCM access, we should add RPC attributes https://github.com/open-telemetry/opentelemetry-specification/blob/v0.7.0/specification/trace/semantic_conventions/rpc.md
     * @param node
     * @param run
     */
    @Override
    public void onAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {
        try (Scope ignored = setupContext(run, node)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, node);

            String principal = Objects.toString(node.getExecution().getAuthentication(), "#null#");
            LOGGER.log(Level.INFO, () -> node.getDisplayFunctionName() + " - principal: " + principal);


            Span atomicStepSpan = getTracer().spanBuilder(node.getDisplayFunctionName())
                    .setParent(Context.current())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, node.getDisplayFunctionName())
                    .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_USER, principal)
                    .startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > " + node.getDisplayFunctionName() + " - begin " + OtelUtils.toDebugString(atomicStepSpan));

            getTracerService().putSpan(run, atomicStepSpan, node);
        }
    }

    @Override
    public void onAfterAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {
        endCurrentSpan(node, run);
    }

    @Override
    public void onStartParallelStepBranch(@Nonnull StepStartNode stepStartNode, @Nonnull String branchName, @Nonnull WorkflowRun run) {
        try (Scope ignored = setupContext(run, stepStartNode)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, stepStartNode);

            Span atomicStepSpan = getTracer().spanBuilder("Parallel branch: " + branchName)
                    .setParent(Context.current())
                    .setAttribute("jenkins.pipeline.step.type", stepStartNode.getDisplayFunctionName())
                    .startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > " + stepStartNode.getDisplayFunctionName() + " - begin " + OtelUtils.toDebugString(atomicStepSpan));

            getTracerService().putSpan(run, atomicStepSpan, stepStartNode);
        }
    }

    @Override
    public void onEndParallelStepBranch(@Nonnull StepEndNode node, @Nonnull String branchName, @Nonnull WorkflowRun run) {
        endCurrentSpan(node, run);
    }

    private void endCurrentSpan(FlowNode node, WorkflowRun run) {
        try (Scope ignored = setupContext(run, node)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, node);

            Span span = getTracerService().getSpan(run, node);
            ErrorAction errorAction = node.getError();
            if (errorAction == null) {
                span.setStatus(StatusCode.OK);
            } else {
                Throwable throwable = errorAction.getError();
                span.recordException(throwable);
                span.setStatus(StatusCode.ERROR, throwable.getMessage());
            }
            span.end();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - < " + node.getDisplayFunctionName() + " - end " + OtelUtils.toDebugString(span));

            getTracerService().removePipelineStepSpan(run, node, span);
        }
    }

    @Override
    public void notifyOfNewStep(@Nonnull Step step, @Nonnull StepContext context) {
        try {
            WorkflowRun run = context.get(WorkflowRun.class);
            FlowNode node = context.get(FlowNode.class);
            Computer computer = context.get(Computer.class);
            if (computer == null || node == null || run == null) {
                LOGGER.log(Level.FINER, () -> "No run, flowNode or computer, skip. Run:" + run + ", flowNode: " + node + ", computer:" + computer);
                return;
            }
            if (computer.getAction(OpenTelemetryAttributesAction.class) == null) {
                LOGGER.log(Level.WARNING, "Unexpected missing " + OpenTelemetryAttributesAction.class + " on " + computer);
                String hostName = computer.getHostName();
                OpenTelemetryAttributesAction openTelemetryAttributesAction = new OpenTelemetryAttributesAction();
                openTelemetryAttributesAction.getAttributes().put(ResourceAttributes.HOST_HOSTNAME, hostName);
                computer.addAction(openTelemetryAttributesAction);
            }
            OpenTelemetryAttributesAction openTelemetryAttributesAction = computer.getAction(OpenTelemetryAttributesAction.class);

            try (Scope ignored = setupContext(run, node)) {
                Span currentSpan = Span.current();
                LOGGER.log(Level.FINE, () -> "Add resource attributes to span " + OtelUtils.toDebugString(currentSpan) + " - " + openTelemetryAttributesAction);
                for (Map.Entry<AttributeKey<?>, Object> entry : openTelemetryAttributesAction.getAttributes().entrySet()) {
                    AttributeKey<?> attributeKey = entry.getKey();
                    Object value = verifyNotNull(entry.getValue());
                    currentSpan.setAttribute((AttributeKey<? super Object>) attributeKey, value);
                }
            }
        } catch (IOException | InterruptedException | RuntimeException e) {
            LOGGER.log(Level.WARNING,"Exception processing " + step + " - " + context, e);
        }
    }

    /**
     * @return {@code null} if no {@link Span} has been created for the {@link Run} of the given {@link FlowNode}
     */
    @CheckForNull
    @MustBeClosed
    protected Scope setupContext(WorkflowRun run, @Nonnull FlowNode node) {
        run = verifyNotNull(run, "%s No run found for node %s", run, node);
        Span span = this.otelTraceService.getSpan(run, node);

        Scope scope = span.makeCurrent();
        Context.current().with(RunContextKey.KEY, run).with(FlowNodeContextKey.KEY, node);
        return scope;
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

    @Override
    public String toString() {
        return "TracingPipelineListener{}";
    }
}
