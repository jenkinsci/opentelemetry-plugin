package io.jenkins.plugins.opentelemetry.job;

import static com.google.common.base.Verify.*;

import com.google.errorprone.annotations.MustBeClosed;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Platform;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.FlowNodeContextKey;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.RunContextKey;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.job.jenkins.AbstractPipelineListener;
import io.jenkins.plugins.opentelemetry.job.jenkins.PipelineListener;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;


@Extension
public class MonitoringPipelineListener extends AbstractPipelineListener implements PipelineListener {
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
            LOGGER.log(Level.INFO, () -> run.getFullDisplayName() + " - stage(" + stageName + ") - begin " + OtelUtils.toDebugString(stageSpan));

            getTracerService().putSpan(run, stageSpan, stepStartNode);
        }
    }

    @Override
    public void onEndStageStep(@Nonnull StepEndNode node, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        endCurrentSpan(node, run);
    }

    @Override
    public void onAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {
        try (Scope ignored = setupContext(run, node)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, node);

            try {
                // FIXME
                TaskListener listener = node.getExecution().getOwner().getListener();
                EnvVars envVars = run.getEnvironment(listener);
                LOGGER.log(Level.INFO, () -> node.getDisplayFunctionName() + " - envVars: " + envVars);

                Platform platform = envVars.getPlatform();
                LOGGER.log(Level.INFO, () -> node.getDisplayFunctionName() + " - platform: " + platform);
            } catch (IOException|InterruptedException e) {
                e.printStackTrace();
            }
            String principal = Objects.toString(node.getExecution().getAuthentication(), "#null#");
            LOGGER.log(Level.INFO, () -> node.getDisplayFunctionName() +  " - principal: " + principal);


            Span atomicStepSpan = getTracer().spanBuilder(node.getDisplayFunctionName())
                    .setParent(Context.current())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, node.getDisplayFunctionName())
                    .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_USER, principal)
                    .startSpan();
            LOGGER.log(Level.INFO, () -> run.getFullDisplayName() + " - > " + node.getDisplayFunctionName() + " - begin " + OtelUtils.toDebugString(atomicStepSpan));

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
            LOGGER.log(Level.INFO, () -> run.getFullDisplayName() + " - > " + stepStartNode.getDisplayFunctionName() + " - begin " + OtelUtils.toDebugString(atomicStepSpan));

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
            LOGGER.log(Level.INFO, () -> run.getFullDisplayName() + " - < " + node.getDisplayFunctionName() + " - end " + OtelUtils.toDebugString(span));

            getTracerService().removePipelineStepSpan(run, node, span);
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
