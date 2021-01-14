package io.jenkins.plugins.opentelemetry.trace.deprecated;

import static com.google.common.base.Verify.*;

import com.google.errorprone.annotations.MustBeClosed;
import hudson.model.Queue;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.trace.OtelTraceService;
import io.jenkins.plugins.opentelemetry.trace.deprecated.TracingGraphListener;
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

/**
 * {@link GraphListener} that setups the OpenTelemetry {@link io.opentelemetry.context.Context}
 * with the current {@link Span}.
 */
public abstract class OtelContextAwareAbstractGraphListener implements GraphListener {

    private final static Logger LOGGER = Logger.getLogger(TracingGraphListener.class.getName());

    private OtelTraceService otelTraceService;
    private Tracer tracer;

    @Override
    public final void onNewHead(FlowNode node) {
        try (Scope ignored = setupContext(node)) {
            verifyNotNull(ignored, "No span found for node %s", node);
            this._onNewHead(node);
        }
    }

    protected void _onNewHead(FlowNode node) {
    }

    /**
     * @return {@code null} if no {@link Span} has been created for the {@link Run} of the given {@link FlowNode}
     */
    @CheckForNull
    @MustBeClosed
    protected Scope setupContext(@Nonnull FlowNode node) {
        Run run = getWorkflowRun(node);
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

    @CheckForNull
    public WorkflowRun getWorkflowRun(@Nonnull FlowNode flowNode) {
        Queue.Executable executable;
        try {
            executable = flowNode.getExecution().getOwner().getExecutable();
        } catch (IOException e) {
            // Ignore exception. Likely to be a `new IOException("not implemented")` thrown by
            // org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner.DummyOwner.getExecutable
            return null;
        }

        if (executable instanceof WorkflowRun) {
            return (WorkflowRun) executable;
        }
        return null;
    }

}
