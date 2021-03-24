/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.*;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.OtelContextAwareAbstractRunListener;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.RunContextKey;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongValueObserver;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Verify.verifyNotNull;

@Extension
public class MonitoringRunListener extends OtelContextAwareAbstractRunListener {

    protected static final Logger LOGGER = Logger.getLogger(MonitoringRunListener.class.getName());

    private AtomicInteger activeRun;
    private LongValueObserver activeRunObserver;
    private LongCounter runLaunchedCounter;
    private LongCounter runStartedCounter;
    private LongCounter runCompletedCounter;
    private LongCounter runAbortedCounter;

    private SpanNamingStrategy spanNamingStrategy;

    @PostConstruct
    public void postConstruct() {
        activeRun = new AtomicInteger();
        activeRunObserver = getMeter().longValueObserverBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_ACTIVE)
                .setDescription("Gauge of active jobs")
                .setUnit("1")
                .setUpdater(longResult -> this.activeRun.get())
                .build();
        runLaunchedCounter =
                getMeter().longCounterBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_LAUNCHED)
                        .setDescription("Job launched")
                        .setUnit("1")
                        .build();
        runStartedCounter =
                getMeter().longCounterBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_STARTED)
                        .setDescription("Job started")
                        .setUnit("1")
                        .build();
        runAbortedCounter =
                getMeter().longCounterBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_ABORTED)
                        .setDescription("Job aborted")
                        .setUnit("1")
                        .build();
        runCompletedCounter =
                getMeter().longCounterBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_COMPLETED)
                        .setDescription("Job completed")
                        .setUnit("1")
                        .build();
    }

    @Override
    public void _onInitialize(Run run) {
        LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - onInitialize");
        if (this.getTraceService().getSpan(run) != null) {
            LOGGER.log(Level.WARNING, () -> run.getFullDisplayName() + " - Unexpected existing span: " + this.getTraceService().getSpan(run));
        }
        activeRun.incrementAndGet();

        String rootSpanName = this.spanNamingStrategy.getRootSpanName(run);
        String runUrl = Objects.toString(Jenkins.get().getRootUrl(), "") + run.getUrl();
        SpanBuilder rootSpanBuilder = getTracer().spanBuilder(rootSpanName)
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, rootSpanName)
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_NAME, run.getParent().getFullDisplayName())
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_URL, runUrl)
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) run.getNumber())
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_TYPE, OtelUtils.getProjectType(run));

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
        String traceId = rootSpan.getSpanContext().getTraceId();
        String spanId = rootSpan.getSpanContext().getSpanId();
        MonitoringAction monitoringAction = new MonitoringAction(traceId, spanId);
        run.addAction(monitoringAction);

        this.getTraceService().putSpan(run, rootSpan);
        try (final Scope rootSpanScope = rootSpan.makeCurrent()) {
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - begin root " + OtelUtils.toDebugString(rootSpan));


            // START initialize span
            Span startSpan = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.JENKINS_JOB_SPAN_PHASE_START_NAME)
                    .setParent(Context.current().with(rootSpan))
                    .startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - begin " + OtelUtils.toDebugString(startSpan));

            this.getTraceService().putSpan(run, startSpan);
            try (final Scope startSpanScope = startSpan.makeCurrent()) {
                this.runLaunchedCounter.add(1);
            }
        }
    }

    @Override
    public void _onStarted(Run run, TaskListener listener) {
        try (Scope parentScope = endPipelinePhaseSpan(run)) {
            Span runSpan = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.JENKINS_JOB_SPAN_PHASE_RUN_NAME).setParent(Context.current()).startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - begin " + OtelUtils.toDebugString(runSpan));
            runSpan.makeCurrent();
            this.getTraceService().putSpan(run, runSpan);
            this.runStartedCounter.add(1);
        }
    }

    @Override
    public void _onCompleted(Run run, @NonNull TaskListener listener) {
        try (Scope parentScope = endPipelinePhaseSpan(run)) {
            Span finalizeSpan = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.JENKINS_JOB_SPAN_PHASE_FINALIZE_NAME).setParent(Context.current()).startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - begin " + OtelUtils.toDebugString(finalizeSpan));
            finalizeSpan.makeCurrent();
            this.getTraceService().putSpan(run, finalizeSpan);
        }
    }

    @MustBeClosed
    @Nonnull
    protected Scope endPipelinePhaseSpan(@Nonnull Run run) {
        Span pipelinePhaseSpan = verifyNotNull(Span.current(), "No pipelinePhaseSpan found in context");
        pipelinePhaseSpan.end();
        LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - end " + OtelUtils.toDebugString(pipelinePhaseSpan));

        this.getTraceService().removeJobPhaseSpan(run, pipelinePhaseSpan);
        Span newCurrentSpan = verifyNotNull(this.getTraceService().getSpan(run), "Failure to find pipeline root span for %s", run);
        Scope newScope = newCurrentSpan.makeCurrent();
        Context.current().with(RunContextKey.KEY, run);
        return newScope;
    }

    @Override
    public void _onFinalized(Run run) {

        try (Scope parentScope = endPipelinePhaseSpan(run)) {
            Span parentSpan = Span.current();
            parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_DURATION_MILLIS, run.getDuration());
            Result runResult = run.getResult();
            if (runResult == null) {
                parentSpan.setStatus(StatusCode.UNSET);
            } else {
                parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_COMPLETED, runResult.completeBuild);
                String description = run.getDescription();
                if (description != null) {
                    parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_DESCRIPTION, description);
                }
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
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - end " + OtelUtils.toDebugString(parentSpan));

            this.getTraceService().removeJobPhaseSpan(run, parentSpan);

            this.getTraceService().purgeRun(run);

            LOGGER.log(Level.FINE, () -> "Increment completion counters");
            this.runCompletedCounter.add(1);
            Result result = verifyNotNull(run.getResult(), "%s", run);

            if (!result.isCompleteBuild()) {
                this.runAbortedCounter.add(1);
            }
        } finally {
            activeRun.decrementAndGet();
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

    @Inject
    public void setSpanNamingStrategy(SpanNamingStrategy spanNamingStrategy) {
        this.spanNamingStrategy = spanNamingStrategy;
    }
}
