package io.jenkins.plugins.opentelemetry;

import hudson.model.Queue;
import hudson.model.Run;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.logging.Logger;

import static com.google.common.base.Verify.verifyNotNull;

public class OpenTelemetryContextAwareGraphListener implements GraphListener {

    private final static Logger LOGGER = Logger.getLogger(OpenTelemetryGraphListener.class.getName());

    private OpenTelemetryTracerService openTelemetryTracerService;
    private Tracer tracer;
    private OpenTelemetry openTelemetry;

    @Override
    public final void onNewHead(FlowNode node) {
        try (Scope ignored = setupContext(node)) {
            // verifyNotNull(ignored, "No span found for node %s", node);
            this._onNewHead(node);
        }
    }

    protected void _onNewHead(FlowNode node) {
    }

    /**
     * @return {@code null} if no {@link Span} has been created for the {@link Run} of the given {@link FlowNode}
     */
    @CheckForNull
    protected Scope setupContext(@Nonnull FlowNode node) {
        Run run = getWorkflowRun(node);
        Span span = this.openTelemetryTracerService.getSpan(run);
        if (span == null) {
            return null;
        } else {
            Scope scope = span.makeCurrent();
            return scope;
        }
    }

    @Inject
    public final void setOpenTelemetryTracerService(OpenTelemetryTracerService openTelemetryTracerService) {
        this.openTelemetryTracerService = openTelemetryTracerService;
        this.openTelemetryTracerService = openTelemetryTracerService;
        this.openTelemetry = openTelemetryTracerService.getOpenTelemetry();
        this.tracer = this.openTelemetry.getTracer("jenkins");
    }

    public OpenTelemetryTracerService getOpenTelemetryTracerService() {
        return openTelemetryTracerService;
    }

    public Tracer getTracer() {
        return tracer;
    }

    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    @CheckForNull
    public WorkflowRun getWorkflowRun(@Nonnull FlowNode flowNode) {
        Queue.Executable exec;
        try {
            exec = flowNode.getExecution().getOwner().getExecutable();
        } catch (IOException e) {
            // Ignore exception. Likely to be a `new IOException("not implemented")` thrown by
            // org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner.DummyOwner.getExecutable
            return null;
        }

        if (exec instanceof WorkflowRun) {
            return (WorkflowRun) exec;
        }
        return null;
    }

}
