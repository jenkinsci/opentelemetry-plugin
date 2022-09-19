/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Node;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.job.cause.CauseHandler;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.OtelContextAwareAbstractRunListener;
import io.jenkins.plugins.opentelemetry.job.runhandler.RunHandler;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.logs.LogEmitter;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.build.BuildUpstreamCause;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Verify.verifyNotNull;

@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class MonitoringRunListener extends OtelContextAwareAbstractRunListener {

    protected static final Logger LOGGER = Logger.getLogger(MonitoringRunListener.class.getName());

    private AtomicInteger activeRun;
    private List<CauseHandler> causeHandlers;
    private LongCounter runLaunchedCounter;
    private LongCounter runStartedCounter;
    private LongCounter runCompletedCounter;
    private LongCounter runAbortedCounter;
    private List<RunHandler> runHandlers;

    @PostConstruct
    public void postConstruct() {

    }

    @Override
    public void afterSdkInitialized(Meter meter, LogEmitter logEmitter, Tracer tracer, ConfigProperties configProperties) {
        super.afterSdkInitialized(meter, logEmitter, tracer, configProperties);

        // CAUSE HANDLERS
        List<CauseHandler> causeHandlers = new ArrayList(ExtensionList.lookup(CauseHandler.class));
        causeHandlers.stream().forEach(causeHandler -> causeHandler.configure(configProperties));
        Collections.sort(causeHandlers);
        this.causeHandlers = causeHandlers;

        // RUN HANDLERS
        List<RunHandler> runHandlers = new ArrayList<>(ExtensionList.lookup(RunHandler.class));
        runHandlers.stream().forEach(runHandler -> runHandler.configure(configProperties));
        Collections.sort(runHandlers);
        this.runHandlers = runHandlers;

        // METRICS
        activeRun = new AtomicInteger();
        getState().registerInstrument(
            meter.gaugeBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_ACTIVE)
                .ofLongs()
                .setDescription("Gauge of active jobs")
                .setUnit("1")
                .buildWithCallback(valueObserver -> this.activeRun.get()));
        runLaunchedCounter =
                meter.counterBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_LAUNCHED)
                        .setDescription("Job launched")
                        .setUnit("1")
                        .build();
        runStartedCounter =
                meter.counterBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_STARTED)
                        .setDescription("Job started")
                        .setUnit("1")
                        .build();
        runAbortedCounter =
                meter.counterBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_ABORTED)
                        .setDescription("Job aborted")
                        .setUnit("1")
                        .build();
        runCompletedCounter =
                meter.counterBuilder(JenkinsSemanticMetrics.CI_PIPELINE_RUN_COMPLETED)
                        .setDescription("Job completed")
                        .setUnit("1")
                        .build();

        LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins build executions...");
    }

    @NonNull
    public List<CauseHandler> getCauseHandlers() {
        return Preconditions.checkNotNull(causeHandlers);
    }

    @NonNull
    public CauseHandler getCauseHandler(@NonNull Cause cause) throws NoSuchElementException {
        return getCauseHandlers().stream().filter(ch -> ch.isSupported(cause)).findFirst().get();
    }

    @Override
    public void _onInitialize(@NonNull Run run) {
        LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - onInitialize");

        activeRun.incrementAndGet();

        RunHandler runHandler = getRunHandlers().stream().filter(rh -> rh.canCreateSpanBuilder(run)).findFirst()
            .orElseThrow((Supplier<RuntimeException>) () -> new IllegalStateException("No RunHandler found for run " + run.getClass() + " - " + run));
        SpanBuilder rootSpanBuilder = runHandler.createSpanBuilder(run, getTracer());

        rootSpanBuilder.setSpanKind(SpanKind.SERVER);
        String runUrl = Objects.toString(Jenkins.get().getRootUrl(), "") + run.getUrl();

        // TODO move this to a pluggable span enrichment API with implementations for different observability backends
        rootSpanBuilder
                .setAttribute(JenkinsOtelSemanticAttributes.ELASTIC_TRANSACTION_TYPE, "job");

        rootSpanBuilder
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, run.getParent().getFullName())
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_NAME, run.getParent().getFullDisplayName())
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_URL, runUrl)
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) run.getNumber())
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_TYPE, OtelUtils.getProjectType(run));

        // CULPRITS
        Set<User> culpritIds;
        if (run instanceof WorkflowRun) {
            culpritIds = ((WorkflowRun) run).getCulprits();
        } else if (run instanceof AbstractBuild) {
            culpritIds = ((AbstractBuild) run).getCulprits();
        } else {
            culpritIds = null;
        }
        if (culpritIds != null) {
            rootSpanBuilder
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_COMMITTERS,
                    culpritIds.stream().map(p -> p.getId()).collect(Collectors.toList()));
        }

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

        // CAUSES
        List<String> causesDescriptions = ((List<Cause>) run.getCauses()).stream().map(c -> getCauseHandler(c).getStructuredDescription(c)).collect(Collectors.toList());
        rootSpanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_CAUSE, causesDescriptions);

        Optional optCause = run.getCauses().stream().findFirst();
        optCause.ifPresent(cause -> {
                if (cause instanceof Cause.UpstreamCause) {
                    Cause.UpstreamCause upstreamCause = (Cause.UpstreamCause) cause;
                    Run upstreamRun = upstreamCause.getUpstreamRun();
                    if (upstreamRun == null) {
                        // hudson.model.Cause.UpstreamCause.getUpstreamRun() can return null, probably if upstream job or build has been deleted.
                    } else {
                        MonitoringAction monitoringAction = upstreamRun.getAction(MonitoringAction.class);
                        Map<String, String> carrier;
                        if (monitoringAction == null) {
                            // unclear why this could happen. Maybe during the installation of the plugin if the plugin is
                            // installed while a parent job triggers a downstream job
                            carrier = Collections.emptyMap();
                        } else if (upstreamCause instanceof BuildUpstreamCause) {
                            BuildUpstreamCause buildUpstreamCause = (BuildUpstreamCause) cause;
                            String upstreamNodeId = buildUpstreamCause.getNodeId();
                            carrier = monitoringAction.getContext(upstreamNodeId);
                        } else {
                            carrier = monitoringAction.getRootContext();
                        }
                        Context context = W3CTraceContextPropagator.getInstance().extract(Context.current(), carrier, new TextMapGetter<Map<String, String>>() {
                            @Override
                            public Iterable<String> keys(Map<String, String> carrier) {
                                return carrier.keySet();
                            }

                            @Nullable
                            @Override
                            public String get(@Nullable Map<String, String> carrier, String key) {
                                return carrier == null ? null : carrier.get(key);
                            }
                        });
                        rootSpanBuilder.setParent(context);

                    }
                } else {
                    // No special processing for this Cause
                }
            }

        );

        // START ROOT SPAN
        Span rootSpan = rootSpanBuilder.startSpan();
        String traceId = rootSpan.getSpanContext().getTraceId();
        String spanId = rootSpan.getSpanContext().getSpanId();
        // TODO better pattern to retrieve the name of the created root span
        String rootSpanName = rootSpan instanceof ReadWriteSpan? ((ReadWriteSpan) rootSpan).getName() : null; // when tracer is no-op, span is NOT a ReadWriteSpan
        MonitoringAction monitoringAction = new MonitoringAction(traceId, spanId, rootSpanName);
        run.addAction(monitoringAction);

        this.getTraceService().putSpan(run, rootSpan);
        try (final Scope rootSpanScope = rootSpan.makeCurrent()) {
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - begin root " + OtelUtils.toDebugString(rootSpan));

            Map<String, String> context = new HashMap<>();
            W3CTraceContextPropagator.getInstance().inject(Context.current(), context, (carrier, key, value) -> carrier.put(key, value));
            monitoringAction.addRootContext(context);

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
    public void _onStarted(@NonNull Run run, @NonNull TaskListener listener) {
        try (Scope parentScope = endPipelinePhaseSpan(run)) {
            Span runSpan = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.JENKINS_JOB_SPAN_PHASE_RUN_NAME).setParent(Context.current()).startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - begin " + OtelUtils.toDebugString(runSpan));
            try (Scope scope = runSpan.makeCurrent()) {
                this.getTraceService().putSpan(run, runSpan);
                // Support non-pipeline jobs
                if (run instanceof AbstractBuild) {
                    this.getTraceService().putSpan((AbstractBuild) run, runSpan);
                }
                this.runStartedCounter.add(1);
            }
        }
    }

    @Override
    public void _onCompleted(@NonNull Run run, @NonNull TaskListener listener) {
        try (Scope parentScope = endPipelinePhaseSpan(run)) {
            Span finalizeSpan = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.JENKINS_JOB_SPAN_PHASE_FINALIZE_NAME).setParent(Context.current()).startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - begin " + OtelUtils.toDebugString(finalizeSpan));
            try (Scope scope = finalizeSpan.makeCurrent()) {
                this.getTraceService().putSpan(run, finalizeSpan);
            }
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
        return newCurrentSpan.makeCurrent();
    }

    @Override
    public void _onFinalized(@NonNull Run run) {

        try (Scope parentScope = endPipelinePhaseSpan(run)) {
            Span parentSpan = Span.current();
            parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_DURATION_MILLIS, run.getDuration());
            String description = run.getDescription(); // make spotbugs happy extracting a variable
            if (description != null) {
                parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_DESCRIPTION, description);
            }
            if (OtelUtils.isMultibranch(run)) {
                parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_MULTIBRANCH_TYPE, OtelUtils.getMultibranchType(run));
            }

            Result runResult = run.getResult();
            if (runResult == null) {
                // illegal state, job should no longer be running
                parentSpan.setStatus(StatusCode.UNSET);
            } else {
                parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_COMPLETED, runResult.completeBuild);
                parentSpan.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_RESULT, Objects.toString(runResult, null));

                if (Result.SUCCESS.equals(runResult)) {
                    parentSpan.setStatus(StatusCode.OK, runResult.toString());
                } else if (Result.FAILURE.equals(runResult) || Result.UNSTABLE.equals(runResult)){
                    parentSpan.setAttribute(SemanticAttributes.EXCEPTION_TYPE, "PIPELINE_" + runResult);
                    parentSpan.setAttribute(SemanticAttributes.EXCEPTION_MESSAGE, "PIPELINE_" + runResult);
                    parentSpan.setStatus(StatusCode.ERROR, runResult.toString());
                } else if (Result.ABORTED.equals(runResult) || Result.NOT_BUILT.equals(runResult)) {
                    parentSpan.setStatus(StatusCode.UNSET, runResult.toString());
                }
            }
            // NODE
            if (run instanceof AbstractBuild) {
                Node node = ((AbstractBuild) run).getBuiltOn();
                if (node != null) {
                    parentSpan.setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_AGENT_LABEL, node.getLabelString());
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

    @Nonnull
    protected List<RunHandler> getRunHandlers() {
        return Preconditions.checkNotNull(this.runHandlers);
    }
}
