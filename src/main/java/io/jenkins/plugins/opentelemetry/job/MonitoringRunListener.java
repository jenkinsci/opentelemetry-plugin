/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
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
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.job.cause.CauseHandler;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.OtelContextAwareAbstractRunListener;
import io.jenkins.plugins.opentelemetry.job.runhandler.RunHandler;
import io.jenkins.plugins.opentelemetry.queue.RemoteSpanAction;
import io.jenkins.plugins.opentelemetry.semconv.CicdMetrics;
import io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsMetrics;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.TraceStateBuilder;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.incubating.CicdIncubatingAttributes;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.build.BuildUpstreamCause;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import static com.google.common.base.Verify.verifyNotNull;

/**
 * TODO support reconfiguration
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class MonitoringRunListener extends OtelContextAwareAbstractRunListener implements OpenTelemetryLifecycleListener {
    static final String PIPELINE_NAME_OTHER = "#other#";

    static final Pattern MATCH_ANYTHING = Pattern.compile(".*");
    static final Pattern MATCH_NOTHING = Pattern.compile("$^");

    // TODO support configurability of these histogram buckets. Note that the conversion from a string to a list of
    //  doubles will require boilerplate so we are interested in getting user feedback before implementing this.
    static final List<Double> DURATION_SECONDS_BUCKETS =
        List.of(1D, 2D, 4D, 8D, 16D, 32D, 64D, 128D, 256D, 512D, 1024D, 2048D, 4096D, 8192D);

    protected static final Logger LOGGER = Logger.getLogger(MonitoringRunListener.class.getName());

    private AtomicInteger activeRunGauge;
    private List<CauseHandler> causeHandlers;
    /**
     * @deprecated use {@link #cicdPipelineRunDurationHistogram}
     */
    @Deprecated
    private DoubleHistogram runDurationHistogram;
    private LongCounter runLaunchedCounter;
    private LongCounter runStartedCounter;
    private LongCounter runCompletedCounter;
    private LongCounter runAbortedCounter;
    private LongCounter runSuccessCounter;
    private LongCounter runFailedCounter;
    private List<RunHandler> runHandlers;
    @VisibleForTesting
    Pattern runDurationHistogramAllowList;
    @VisibleForTesting
    Pattern runDurationHistogramDenyList;

    private DoubleHistogram cicdPipelineRunDurationHistogram;
    private LongUpDownCounter cicdPipelineRunActiveCounter;

    @PostConstruct
    public void postConstruct() {
        LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins build executions...");
        Meter meter = getMeter();
        ConfigProperties configProperties = getConfigProperties();

        // CAUSE HANDLERS
        List<CauseHandler> causeHandlers = new ArrayList<>(ExtensionList.lookup(CauseHandler.class));
        causeHandlers.forEach(causeHandler -> causeHandler.configure(configProperties));
        Collections.sort(causeHandlers);
        this.causeHandlers = causeHandlers;

        // RUN HANDLERS
        List<RunHandler> runHandlers = new ArrayList<>(ExtensionList.lookup(RunHandler.class));
        runHandlers.forEach(runHandler -> runHandler.configure(configProperties));
        Collections.sort(runHandlers);
        this.runHandlers = runHandlers;

        // METRICS
        activeRunGauge = new AtomicInteger();

        runDurationHistogram = meter.histogramBuilder(JenkinsMetrics.CI_PIPELINE_RUN_DURATION)
            .setUnit("s")
            .setExplicitBucketBoundariesAdvice(DURATION_SECONDS_BUCKETS)
            .build();
        runDurationHistogramAllowList = MATCH_ANYTHING; // allow all
        runDurationHistogramDenyList = MATCH_NOTHING; // deny nothing

        cicdPipelineRunDurationHistogram = CicdMetrics.newCiCdPipelineRunDurationHistogram(meter);
        cicdPipelineRunActiveCounter = CicdMetrics.newCiCdPipelineRunActiveCounter(meter);

        meter.gaugeBuilder(JenkinsMetrics.CI_PIPELINE_RUN_ACTIVE)
            .ofLongs()
            .setDescription("Gauge of active jobs")
            .setUnit("{jobs}")
            .buildWithCallback(valueObserver -> valueObserver.record(this.activeRunGauge.get()));
        runLaunchedCounter =
            meter.counterBuilder(JenkinsMetrics.CI_PIPELINE_RUN_LAUNCHED)
                .setDescription("Job launched")
                .setUnit("{jobs}")
                .build();
        runStartedCounter =
            meter.counterBuilder(JenkinsMetrics.CI_PIPELINE_RUN_STARTED)
                .setDescription("Job started")
                .setUnit("{jobs}")
                .build();
        runSuccessCounter =
            meter.counterBuilder(JenkinsMetrics.CI_PIPELINE_RUN_SUCCESS)
                .setDescription("Job succeed")
                .setUnit("{jobs}")
                .build();
        runFailedCounter =
            meter.counterBuilder(JenkinsMetrics.CI_PIPELINE_RUN_FAILED)
                .setDescription("Job failed")
                .setUnit("{jobs}")
                .build();
        runAbortedCounter =
            meter.counterBuilder(JenkinsMetrics.CI_PIPELINE_RUN_ABORTED)
                .setDescription("Job aborted")
                .setUnit("{jobs}")
                .build();
        runCompletedCounter =
            meter.counterBuilder(JenkinsMetrics.CI_PIPELINE_RUN_COMPLETED)
                .setDescription("Job completed")
                .setUnit("{jobs}")
                .build();
    }

    @Override
    public void afterConfiguration(ConfigProperties configProperties) {
        Pattern newRunDurationHistogramAllowList;
        Pattern newRunDurationHistogramDenyList;
        try {
            newRunDurationHistogramAllowList = Optional
                .ofNullable(configProperties.getString(ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_RUN_DURATION_ALLOW_LIST.asProperty()))
                .map(Pattern::compile)
                .orElse(MATCH_NOTHING);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex for '" +
                    ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_RUN_DURATION_ALLOW_LIST.asProperty() + "'", e);
        }
        try {
             newRunDurationHistogramDenyList = Optional
                .ofNullable(configProperties.getString(ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_RUN_DURATION_DENY_LIST.asProperty()))
                .map(Pattern::compile)
                .orElse(MATCH_NOTHING);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex for '" +
                ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_RUN_DURATION_DENY_LIST.asProperty() + "'", e);
        }
        this.runDurationHistogramAllowList = newRunDurationHistogramAllowList;
        this.runDurationHistogramDenyList = newRunDurationHistogramDenyList;
    }

    @NonNull
    public List<CauseHandler> getCauseHandlers() {
        return Preconditions.checkNotNull(causeHandlers);
    }

    @NonNull
    public CauseHandler getCauseHandler(@NonNull Cause cause) throws NoSuchElementException {
        return getCauseHandlers().stream().filter(ch -> ch.isSupported(cause)).findFirst().orElseThrow();
    }

    @Override
    public void _onInitialize(@NonNull Run<?, ?> run) {
        LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - onInitialize");

        activeRunGauge.incrementAndGet();
        cicdPipelineRunActiveCounter.add(1, Attributes.of(
            CicdIncubatingAttributes.CICD_PIPELINE_NAME, PIPELINE_NAME_OTHER, // FIXME CARDINALITY PROTECTION
            CicdIncubatingAttributes.CICD_PIPELINE_RUN_STATE, CicdIncubatingAttributes.CicdPipelineRunStateIncubatingValues.PENDING
        ));

        RunHandler runHandler = getRunHandlers().stream().filter(rh -> rh.canCreateSpanBuilder(run)).findFirst()
            .orElseThrow((Supplier<RuntimeException>) () -> new IllegalStateException("No RunHandler found for run " + run.getClass() + " - " + run));
        SpanBuilder rootSpanBuilder = runHandler.createSpanBuilder(run, getTracer());

        rootSpanBuilder.setSpanKind(SpanKind.SERVER);
        String runUrl = Objects.toString(Jenkins.get().getRootUrl(), "") + run.getUrl();

        // TODO move this to a pluggable span enrichment API with implementations for different observability backends
        rootSpanBuilder
            .setAttribute(ExtendedJenkinsAttributes.ELASTIC_TRANSACTION_TYPE, "job");

        rootSpanBuilder
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_ID, run.getParent().getFullName())
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_NAME, run.getParent().getFullDisplayName())
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_URL, runUrl)
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_NUMBER, (long) run.getNumber())
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_TYPE, OtelUtils.getProjectType(run));

        // CULPRITS
        Set<User> culpritIds;
        if (run instanceof WorkflowRun) {
            culpritIds = ((WorkflowRun) run).getCulprits();
        } else if (run instanceof AbstractBuild) {
            culpritIds = ((AbstractBuild<?, ?>) run).getCulprits();
        } else {
            culpritIds = null;
        }
        if (culpritIds != null) {
            rootSpanBuilder
                .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_COMMITTERS,
                    culpritIds.stream().map(User::getId).collect(Collectors.toList()));
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
            rootSpanBuilder.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_PARAMETER_NAME, parameterNames);
            rootSpanBuilder.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_PARAMETER_IS_SENSITIVE, parameterIsSensitive);
            rootSpanBuilder.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_PARAMETER_VALUE, nonNullParameterValues);
        }

        // We will use remote context first but may be overridden by local parent context
        Optional.ofNullable(run.getAction(RemoteSpanAction.class))
            .filter(r -> r.getTraceId() != null && r.getSpanId() != null)
            .ifPresent(action -> {
                TraceStateBuilder traceStateBuilder = TraceState.builder();
                Map<String, String> traceStateMap = action.getTraceStateMap();
                traceStateMap.forEach(traceStateBuilder::put);
                SpanContext spanContext = SpanContext.createFromRemoteParent(
                    action.getTraceId(),
                    action.getSpanId(),
                    TraceFlags.fromByte(action.getTraceFlagsAsByte()),
                    traceStateBuilder.build());
                Context context = Context.current().with(Span.wrap(spanContext));
                rootSpanBuilder.setParent(context);
            });

        // CAUSES
        List<String> causesDescriptions = run.getCauses().stream().map(c -> getCauseHandler(c).getStructuredDescription(c)).collect(Collectors.toList());
        rootSpanBuilder.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_CAUSE, causesDescriptions);

        Optional<Cause> optCause = run.getCauses().stream().findFirst();
        optCause.ifPresent(cause -> {
                if (cause instanceof Cause.UpstreamCause upstreamCause) {
                    Run<?, ?> upstreamRun = upstreamCause.getUpstreamRun();
                    if (upstreamRun == null) {
                        // hudson.model.Cause.UpstreamCause.getUpstreamRun() can return null, probably if upstream job or build has been deleted.
                    } else {
                        MonitoringAction monitoringAction = upstreamRun.getAction(MonitoringAction.class);
                        Map<String, String> w3cTraceContext;
                        if (monitoringAction == null) {
                            // unclear why this could happen. Maybe during the installation of the plugin if the plugin is
                            // installed while a parent job triggers a downstream job
                            w3cTraceContext = Collections.emptyMap();
                        } else if (upstreamCause instanceof BuildUpstreamCause) {
                            BuildUpstreamCause buildUpstreamCause = (BuildUpstreamCause) cause;
                            String upstreamNodeId = buildUpstreamCause.getNodeId();
                            w3cTraceContext = monitoringAction.getW3cTraceContext(upstreamNodeId);
                        } else {
                            w3cTraceContext = monitoringAction.getW3cTraceContext();
                        }
                        Context context = W3CTraceContextPropagator.getInstance().extract(Context.current(), w3cTraceContext, new TextMapGetter<Map<String, String>>() {
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

        this.getTraceService().putSpan(run, rootSpan);
        try (final Scope rootSpanScope = rootSpan.makeCurrent()) {
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - begin root " + OtelUtils.toDebugString(rootSpan));

            // START initialize span
            Span startSpan = getTracer().spanBuilder(ExtendedJenkinsAttributes.JENKINS_JOB_SPAN_PHASE_START_NAME)
                .setParent(Context.current().with(rootSpan))
                .startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - begin " + OtelUtils.toDebugString(startSpan));

            this.getTraceService().putRunPhaseSpan(run, startSpan);
            try (final Scope startSpanScope = startSpan.makeCurrent()) {
                this.runLaunchedCounter.add(1);
            }
        }
    }

    @Override
    public void _onStarted(@NonNull Run<?, ?> run, @NonNull TaskListener listener) {
        cicdPipelineRunActiveCounter.add(-1, Attributes.of(
            CicdIncubatingAttributes.CICD_PIPELINE_NAME, PIPELINE_NAME_OTHER, // FIXME CARDINALITY PROTECTION
            CicdIncubatingAttributes.CICD_PIPELINE_RUN_STATE, CicdIncubatingAttributes.CicdPipelineRunStateIncubatingValues.PENDING
        ));
        cicdPipelineRunActiveCounter.add(1, Attributes.of(
            CicdIncubatingAttributes.CICD_PIPELINE_NAME, PIPELINE_NAME_OTHER, // FIXME CARDINALITY PROTECTION
            CicdIncubatingAttributes.CICD_PIPELINE_RUN_STATE, CicdIncubatingAttributes.CicdPipelineRunStateIncubatingValues.EXECUTING
        ));
        try (Scope parentScope = endPipelinePhaseSpan(run)) {
            Span runSpan = getTracer().spanBuilder(ExtendedJenkinsAttributes.JENKINS_JOB_SPAN_PHASE_RUN_NAME).setParent(Context.current()).startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - begin " + OtelUtils.toDebugString(runSpan));
            try (Scope scope = runSpan.makeCurrent()) {
                this.getTraceService().putRunPhaseSpan(run, runSpan);
                this.runStartedCounter.add(1);
            }
        }
    }

    @Override
    public void _onCompleted(@NonNull Run<?, ?> run, @NonNull TaskListener listener) {
        cicdPipelineRunActiveCounter.add(-1, Attributes.of(
            CicdIncubatingAttributes.CICD_PIPELINE_NAME, PIPELINE_NAME_OTHER, // FIXME CARDINALITY PROTECTION
            CicdIncubatingAttributes.CICD_PIPELINE_RUN_STATE, CicdIncubatingAttributes.CicdPipelineRunStateIncubatingValues.EXECUTING
        ));
        cicdPipelineRunActiveCounter.add(1, Attributes.of(
            CicdIncubatingAttributes.CICD_PIPELINE_NAME, PIPELINE_NAME_OTHER, // FIXME CARDINALITY PROTECTION
            CicdIncubatingAttributes.CICD_PIPELINE_RUN_STATE, CicdIncubatingAttributes.CicdPipelineRunStateIncubatingValues.FINALIZING
        ));

        try (Scope ignoredParentScope = endPipelinePhaseSpan(run)) {
            Span finalizeSpan = getTracer().spanBuilder(ExtendedJenkinsAttributes.JENKINS_JOB_SPAN_PHASE_FINALIZE_NAME).setParent(Context.current()).startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - begin " + OtelUtils.toDebugString(finalizeSpan));
            try (Scope ignored = finalizeSpan.makeCurrent()) {
                this.getTraceService().putRunPhaseSpan(run, finalizeSpan);
            }
        }
    }

    @MustBeClosed
    @NonNull
    protected Scope endPipelinePhaseSpan(@NonNull Run<?, ?> run) {
        Span pipelinePhaseSpan = verifyNotNull(Span.current(), "No pipelinePhaseSpan found in context");
        pipelinePhaseSpan.end();
        LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - end " + OtelUtils.toDebugString(pipelinePhaseSpan));

        this.getTraceService().removeJobPhaseSpan(run, pipelinePhaseSpan);
        Span newCurrentSpan = this.getTraceService().getPipelineRootSpan(run);
        return newCurrentSpan.makeCurrent();
    }

    @Override
    public void _onFinalized(@NonNull Run<?, ?> run) {

        try (Scope ignoredParentScope = endPipelinePhaseSpan(run)) {
            Span parentSpan = Span.current();
            parentSpan.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_DURATION_MILLIS, run.getDuration());
            String description = run.getDescription(); // make spotbugs happy extracting a variable
            if (description != null) {
                parentSpan.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_DESCRIPTION, description);
            }
            if (OtelUtils.isMultibranch(run)) {
                parentSpan.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_MULTIBRANCH_TYPE, OtelUtils.getMultibranchType(run));
            }

            Result runResult = run.getResult();
            if (runResult == null) {
                // illegal state, job should no longer be running
                parentSpan.setStatus(StatusCode.UNSET);
            } else {
                parentSpan.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_COMPLETED, runResult.completeBuild);
                parentSpan.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_RESULT, Objects.toString(runResult, null));

                if (Result.SUCCESS.equals(runResult)) {
                    parentSpan.setStatus(StatusCode.OK, runResult.toString());
                } else if (Result.FAILURE.equals(runResult) || Result.UNSTABLE.equals(runResult)) {
                    parentSpan.setAttribute(ExceptionAttributes.EXCEPTION_TYPE, "PIPELINE_" + runResult);
                    parentSpan.setAttribute(ExceptionAttributes.EXCEPTION_MESSAGE, "PIPELINE_" + runResult);
                    parentSpan.setStatus(StatusCode.ERROR, runResult.toString());
                } else if (Result.ABORTED.equals(runResult) || Result.NOT_BUILT.equals(runResult)) {
                    parentSpan.setStatus(StatusCode.UNSET, runResult.toString());
                }
            }
            // NODE
            if (run instanceof AbstractBuild) {
                Node node = ((AbstractBuild<?, ?>) run).getBuiltOn();
                if (node != null) {
                    parentSpan.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_AGENT_LABEL, node.getLabelString());
                    parentSpan.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_AGENT_ID, node.getNodeName());
                    parentSpan.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_AGENT_NAME, node.getDisplayName());
                }
            }
            parentSpan.end();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - end " + OtelUtils.toDebugString(parentSpan));

            this.getTraceService().removeJobPhaseSpan(run, parentSpan);

            this.getTraceService().purgeRun(run);

            Result result = verifyNotNull(run.getResult(), "%s", run);

            if (result.isCompleteBuild()) {
                LOGGER.log(Level.FINE, () -> "Increment completion counters");
                this.runCompletedCounter.add(1);
                if (result.equals(Result.SUCCESS)) {
                    this.runSuccessCounter.add(1);
                } else {
                    this.runFailedCounter.add(1);
                }
            } else {
                this.runAbortedCounter.add(1);
            }

            String jobFullName = run.getParent().getFullName();
            String pipelineId =
                runDurationHistogramAllowList.matcher(jobFullName).matches()
                    &&
                    !runDurationHistogramDenyList.matcher(jobFullName).matches() ?
                    jobFullName : PIPELINE_NAME_OTHER;
            runDurationHistogram.record(
                TimeUnit.SECONDS.convert(run.getDuration(), TimeUnit.MILLISECONDS),
                Attributes.of(
                    ExtendedJenkinsAttributes.CI_PIPELINE_ID, pipelineId,
                    ExtendedJenkinsAttributes.CI_PIPELINE_RUN_RESULT, result.toString())
            );

            // FIXME CicdIncubatingAttributes.CICD_PIPELINE_RUN_STATE & ErrorAttributes.ERROR_TYPE
            cicdPipelineRunDurationHistogram.record(
                TimeUnit.SECONDS.convert(run.getDuration(), TimeUnit.MILLISECONDS),
                Attributes.of(
                    CicdIncubatingAttributes.CICD_PIPELINE_NAME, pipelineId,
                    CicdIncubatingAttributes.CICD_PIPELINE_RESULT, CicdMetrics.fromJenkinsResultToOtelCicdPipelineResult(result)));

            cicdPipelineRunActiveCounter.add(-1, Attributes.of(
                CicdIncubatingAttributes.CICD_PIPELINE_NAME, PIPELINE_NAME_OTHER, // FIXME CARDINALITY PROTECTION
                CicdIncubatingAttributes.CICD_PIPELINE_RUN_STATE, CicdIncubatingAttributes.CicdPipelineRunStateIncubatingValues.FINALIZING
            ));
        } finally {
            activeRunGauge.decrementAndGet();
        }
    }

    @NonNull
    protected List<RunHandler> getRunHandlers() {
        return Preconditions.checkNotNull(this.runHandlers);
    }
}
