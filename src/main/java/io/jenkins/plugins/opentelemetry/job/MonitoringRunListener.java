/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.base.Strings;
import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Node;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.OtelContextAwareAbstractRunListener;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.RunContextKey;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
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
    private LongCounter runLaunchedCounter;
    private LongCounter runStartedCounter;
    private LongCounter runCompletedCounter;
    private LongCounter runAbortedCounter;

    private SpanNamingStrategy spanNamingStrategy;

    @PostConstruct
    public void postConstruct() {
        activeRun = new AtomicInteger();
        getMeter().gaugeBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_ACTIVE)
            .ofLongs()
            .setDescription("Gauge of active jobs")
            .setUnit("1")
            .buildWithCallback(valueObserver -> this.activeRun.get());
        runLaunchedCounter =
                getMeter().counterBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_LAUNCHED)
                        .setDescription("Job launched")
                        .setUnit("1")
                        .build();
        runStartedCounter =
                getMeter().counterBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_STARTED)
                        .setDescription("Job started")
                        .setUnit("1")
                        .build();
        runAbortedCounter =
                getMeter().counterBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_ABORTED)
                        .setDescription("Job aborted")
                        .setUnit("1")
                        .build();
        runCompletedCounter =
                getMeter().counterBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_COMPLETED)
                        .setDescription("Job completed")
                        .setUnit("1")
                        .build();
    }

    @Override
    public void _onInitialize(Run run) {
        LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - onInitialize");

        activeRun.incrementAndGet();

        String rootSpanName = this.spanNamingStrategy.getRootSpanName(run);
        String runUrl = Objects.toString(Jenkins.get().getRootUrl(), "") + run.getUrl();
        SpanBuilder rootSpanBuilder = getTracer().spanBuilder(rootSpanName)
                .setSpanKind(SpanKind.SERVER);

        // TODO move this to a pluggable span enrichment API with implementations for different observability backends
        // Regarding the value `unknown`, see https://github.com/jenkinsci/opentelemetry-plugin/issues/51
        rootSpanBuilder
                .setAttribute(JenkinsOtelSemanticAttributes.ELASTIC_TRANSACTION_TYPE, "unknown");

        rootSpanBuilder
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
            // Span Attribute Values can NOT be null
            // https://github.com/open-telemetry/opentelemetry-specification/blob/v1.3.0/specification/common/common.md
            List<String> nonNullParameterValues = new ArrayList<>();

            for (ParameterValue parameter : parameters.getParameters()) {
                parameterNames.add(Objects.toString(parameter.getName(), "#NULL#"));
                parameterIsSensitive.add(parameter.isSensitive());
                if (parameter.isSensitive()) {
                    nonNullParameterValues.add("#REDACTED#");
                } else {
                    nonNullParameterValues.add(Objects.toString(parameter.getValue(), "#NULL#"));
                }
            }
            rootSpanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_PARAMETER_NAME, parameterNames);
            rootSpanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_PARAMETER_IS_SENSITIVE, parameterIsSensitive);
            rootSpanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_PARAMETER_VALUE, nonNullParameterValues);
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
            // Support non-pipeline jobs
            if (run instanceof AbstractBuild) {
                this.getTraceService().putSpan((AbstractBuild) run, runSpan);
            }
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
        Span newCurrentSpan = this.getTraceService().getSpan(run);
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
                if (OtelUtils.isMultibranch(run)) {
                    parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_MULTIBRANCH_TYPE, OtelUtils.getMultibranchType(run));
                }
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
                    parentSpan.setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_AGENT_LABEL, Strings.emptyToNull(node.getLabelString()));
                    parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_AGENT_ID, node.getNodeName());
                    parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_AGENT_NAME, node.getDisplayName());
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
