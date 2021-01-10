package io.jenkins.plugins.opentelemetry;

import static com.google.common.base.Verify.*;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.*;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class OpenTelemetryRunListener extends OpenTelemetryContextAwareRunListener<Run> {

    protected static final Logger LOGGER = Logger.getLogger(OpenTelemetryRunListener.class.getName());

    @Override
    public void _onInitialize(Run run) {
        LOGGER.log(Level.INFO, "onInitialize");
        if (this.getOpenTelemetryTracerService().getSpan(run) != null) {
            LOGGER.log(Level.WARNING, "Unexpected existing span: " + this.getOpenTelemetryTracerService().getSpan(run));
        }

        SpanBuilder rootSpanBuilder = getTracer().spanBuilder(run.getParent().getFullName())
                .setAttribute(OpenTelemetryAttributes.CI_PIPELINE_TYPE, "jenkins")
                .setAttribute(OpenTelemetryAttributes.CI_PIPELINE_ID, run.getParent().getFullName())
                .setAttribute(OpenTelemetryAttributes.CI_PIPELINE_NAME, run.getParent().getFullDisplayName())
                .setAttribute(OpenTelemetryAttributes.CI_PIPELINE_RUN_NUMBER, (long) run.getNumber());

        // PARAMETERS
        ParametersAction parameters = run.getAction(ParametersAction.class);
        if (parameters != null) {
            List<String> parameterNames = new ArrayList<>();
            List<Boolean> parameterIsSensitive = new ArrayList<>();
            List<String> parameterValues = new ArrayList<>();

            for (ParameterValue parameter : parameters.getParameters()) {
                parameterNames.add(parameter.getName());
                parameterIsSensitive.add(parameter.isSensitive());
                if (parameter.isSensitive()) {
                    parameterValues.add(null);
                } else {
                    parameterValues.add(Objects.toString(parameter.getValue(), null));
                }
            }
            rootSpanBuilder.setAttribute(OpenTelemetryAttributes.CI_PIPELINE_RUN_PARAMETER_NAME, parameterNames);
            rootSpanBuilder.setAttribute(OpenTelemetryAttributes.CI_PIPELINE_RUN_PARAMETER_IS_SENSITIVE, parameterIsSensitive);
            rootSpanBuilder.setAttribute(OpenTelemetryAttributes.CI_PIPELINE_RUN_PARAMETER_VALUE, parameterValues);
        }

        if (!run.getCauses().isEmpty()) {
            List causes = run.getCauses();
            // TODO
        }

        // START ROOT SPAN
        Span rootSpan = rootSpanBuilder.startSpan();
        this.getOpenTelemetryTracerService().putSpan(run, rootSpan);
        rootSpan.makeCurrent();

        // START initialize span
        Span startSpan = getTracer().spanBuilder("start").setParent(Context.current().with(rootSpan)).startSpan();
        this.getOpenTelemetryTracerService().putSpan(run, startSpan);
        startSpan.makeCurrent();
    }

    @Override
    public void _onStarted(Run run, TaskListener listener) {
        Span previousSpan = this.getOpenTelemetryTracerService().getSpan(run);
        previousSpan.end();
        boolean removed = this.getOpenTelemetryTracerService().removeSpan(run, previousSpan);
        verify(removed, "Failure to remove span %s from %s", previousSpan, run);

        Span parentSpan = this.getOpenTelemetryTracerService().getSpan(run);
        Scope parentSpanContext = parentSpan.makeCurrent();
        verifyNotNull(parentSpan, "Root span not found for %s", run);
        Span runSpan = getTracer().spanBuilder("run").setParent(Context.current().with(parentSpan)).startSpan();
        runSpan.makeCurrent();
        this.getOpenTelemetryTracerService().putSpan(run, runSpan);

        this.getOpenTelemetryTracerService().dumpContext(run,"onStarted", listener.getLogger());

    }

    @Override
    public void _onCompleted(Run run, @NonNull TaskListener listener) {
        Span previousSpan = this.getOpenTelemetryTracerService().getSpan(run);
        previousSpan.end();
        boolean removed = this.getOpenTelemetryTracerService().removeSpan(run, previousSpan);
        verify(removed, "Failure to remove span %s from %s", previousSpan, run);

        Span parentSpan = this.getOpenTelemetryTracerService().getSpan(run);
        Scope parentSpanContext = parentSpan.makeCurrent();
        verifyNotNull(parentSpan, "Root span not found for %s", run);
        Span finalizeSpan = getTracer().spanBuilder("finalise").setParent(Context.current().with(parentSpan)).startSpan();
        finalizeSpan.makeCurrent();
        this.getOpenTelemetryTracerService().putSpan(run, finalizeSpan);

        this.getOpenTelemetryTracerService().dumpContext(run,"onCompleted", listener.getLogger());

    }

    @Override
    public void _onFinalized(Run run) {
        Span previousSpan = this.getOpenTelemetryTracerService().getSpan(run);
        previousSpan.end();
        boolean removed = this.getOpenTelemetryTracerService().removeSpan(run, previousSpan);
        verify(removed, "Failure to remove span %s from %s", previousSpan, run);

        Span parentSpan = this.getOpenTelemetryTracerService().getSpan(run);
        parentSpan.setAttribute(OpenTelemetryAttributes.CI_PIPELINE_RUN_DURATION_MILLIS, run.getDuration());
        Result runResult = run.getResult();
        if (runResult != null) {
            parentSpan.setAttribute(OpenTelemetryAttributes.CI_PIPELINE_RUN_COMPLETED, runResult.completeBuild);
            parentSpan.setAttribute(OpenTelemetryAttributes.CI_PIPELINE_RUN_RESULT, runResult.toString());
        }
        // NODE
        if (run instanceof AbstractBuild) {
            Node node = ((AbstractBuild) run).getBuiltOn();
            if (node != null) {
                parentSpan.setAttribute(OpenTelemetryAttributes.CI_PIPELINE_NODE_ID, node.getNodeName());
                parentSpan.setAttribute(OpenTelemetryAttributes.CI_PIPELINE_NODE_NAME, node.getDisplayName());
            }
        }
        parentSpan.end();
        removed = this.getOpenTelemetryTracerService().removeSpan(run, parentSpan);
        verify(removed, "Failure to remove span %s from %s", previousSpan, run);

        int zombies = this.getOpenTelemetryTracerService().purgeRun(run);
        verify(zombies == 0, "Found %s remaining/non-ended spans on %s", run);
    }

    @Override
    public void _onDeleted(Run run) {
        super.onDeleted(run);
    }

    private void dumpCauses(Run<?, ?> run, StringBuilder buf) {
        for (CauseAction action : run.getActions(CauseAction.class)) {
            for (Cause cause : action.getCauses()) {
                if (buf.length() > 0) buf.append(", ");
                buf.append(cause.getShortDescription());
            }
        }
        if (buf.length() == 0) buf.append("Started");
    }

}
