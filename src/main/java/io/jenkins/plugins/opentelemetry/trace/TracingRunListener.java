package io.jenkins.plugins.opentelemetry.trace;

import static com.google.common.base.Verify.*;

import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.*;
import io.jenkins.plugins.opentelemetry.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.trace.context.RunContextKey;
import io.jenkins.plugins.opentelemetry.trace.context.OtelContextAwareAbstractRunListener;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class TracingRunListener extends OtelContextAwareAbstractRunListener {

    protected static final Logger LOGGER = Logger.getLogger(TracingRunListener.class.getName());

    @Override
    public void _onInitialize(Run run) {
        LOGGER.log(Level.INFO, "onInitialize");
        if (this.getTraceService().getSpan(run) != null) {
            LOGGER.log(Level.WARNING, "Unexpected existing span: " + this.getTraceService().getSpan(run));
        }

        SpanBuilder rootSpanBuilder = getTracer().spanBuilder(run.getParent().getFullName())
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_TYPE, "jenkins")
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, run.getParent().getFullName())
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_NAME, run.getParent().getFullDisplayName())
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) run.getNumber());

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
            rootSpanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_PARAMETER_NAME, parameterNames);
            rootSpanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_PARAMETER_IS_SENSITIVE, parameterIsSensitive);
            rootSpanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_PARAMETER_VALUE, parameterValues);
        }

        if (!run.getCauses().isEmpty()) {
            List causes = run.getCauses();
            // TODO
        }

        // START ROOT SPAN
        Span rootSpan = rootSpanBuilder.startSpan();
        this.getTraceService().putSpan(run, rootSpan);
        rootSpan.makeCurrent();

        // START initialize span
        Span startSpan = getTracer().spanBuilder("Phase: Start").setParent(Context.current().with(rootSpan)).startSpan();
        this.getTraceService().putSpan(run, startSpan);
        startSpan.makeCurrent();
    }

    @Override
    public void _onStarted(Run run, TaskListener listener) {
        try (Scope parentScope = endPipelinePhaseSpan(run)) {
            Span runSpan = getTracer().spanBuilder("Phase: Run").setParent(Context.current()).startSpan();
            runSpan.makeCurrent();
            this.getTraceService().putSpan(run, runSpan);
        }

    }

    @Override
    public void _onCompleted(Run run, @NonNull TaskListener listener) {
        try (Scope parentScope = endPipelinePhaseSpan(run)) {
            Span finalizeSpan = getTracer().spanBuilder("Phase: Finalise").setParent(Context.current()).startSpan();
            finalizeSpan.makeCurrent();
            this.getTraceService().putSpan(run, finalizeSpan);
        }
    }

    @MustBeClosed
    @Nonnull
    protected Scope endPipelinePhaseSpan(@Nonnull Run run) {
        Span pipelinePhaseSpan = Span.current();
        verifyNotNull(pipelinePhaseSpan, "No pipelinePhaseSpan found in context");
        pipelinePhaseSpan.end();
        boolean removed = this.getTraceService().removeSpan(run, pipelinePhaseSpan);
        verify(removed, "Failure to remove pipeline phase span %s from %s", pipelinePhaseSpan, run);
        Span newCurrentSpan = this.getTraceService().getSpan(run);
        verifyNotNull(newCurrentSpan, "Failure to find pipeline root span for %s" , run);
        Scope newScope = newCurrentSpan.makeCurrent();
        Context.current().with(RunContextKey.KEY, run);
        return newScope;
    }

    @Override
    public void _onFinalized(Run run) {
        try(Scope parentScope = endPipelinePhaseSpan(run)) {
            Span parentSpan = Span.current();
            parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_DURATION_MILLIS, run.getDuration());
            Result runResult = run.getResult();
            if (runResult == null) {
                parentSpan.setStatus(StatusCode.UNSET);
            } else {
                parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_COMPLETED, runResult.completeBuild);
                parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_RESULT, runResult.toString());
                StatusCode statusCode = Result.SUCCESS.equals(runResult) ? StatusCode.OK : StatusCode.ERROR;
                parentSpan.setStatus(statusCode);
            }
            // NODE
            if (run instanceof AbstractBuild) {
                Node node = ((AbstractBuild) run).getBuiltOn();
                if (node != null) {
                    parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_NODE_ID, node.getNodeName());
                    parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_NODE_NAME, node.getDisplayName());
                }
            }
            parentSpan.end();
            boolean removed = this.getTraceService().removeSpan(run, parentSpan);
            verify(removed, "Failure to remove span %s from %s", parentSpan, run);

            int zombies = this.getTraceService().purgeRun(run);
            verify(zombies == 0, "Found %s remaining/non-ended spans on %s", zombies, run);
        }
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
