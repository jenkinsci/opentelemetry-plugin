/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Queue;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import jenkins.YesNoMaybe;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionListener;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Records the "Pipeline: compile" span of a build: the time between the creation of its flow execution and its first
 * step, which is the Groovy compilation and the loading of the {@code @Library} libraries.
 * <p>
 * The span is created after the fact, with timestamps, as a child of the "Phase: Run" span, so no scope is open while
 * the libraries load and spans of other builds never get it as parent. A build that fails during the compilation
 * never reaches {@link #onRunning(FlowExecution)}: {@link MonitoringRunListener} calls
 * {@link #onRunCompleted(Run)}, which records the span with an error status.
 * <p>
 * The builds that are compiling are remembered by their {@link FlowExecutionOwner}, so a build that is not compiling
 * (the setting is off, or the flow execution was resumed after a restart) costs a map lookup and does not resolve its
 * run. No failure of this listener may reach the caller: it is called from the run listener that ends the phases of
 * the build.
 */
@Extension(dynamicLoadable = YesNoMaybe.YES)
public class PipelineCompileSpanListener extends FlowExecutionListener {

    private static final Logger LOGGER = Logger.getLogger(PipelineCompileSpanListener.class.getName());

    /** Start of the compile phase of the builds that are compiling. */
    private final Map<FlowExecutionOwner, Instant> compileStarts = new ConcurrentHashMap<>();

    @Inject
    private JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    @Inject
    private OtelTraceService otelTraceService;

    /**
     * @return the listener of this Jenkins controller
     */
    public static PipelineCompileSpanListener get() {
        return ExtensionList.lookupSingleton(PipelineCompileSpanListener.class);
    }

    @Override
    public void onCreated(@NonNull FlowExecution execution) {
        try {
            if (JenkinsOpenTelemetryPluginConfiguration.get().isRecordCompileSpan()) {
                compileStarts.put(execution.getOwner(), Instant.now());
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, e, () -> "Failed to remember the start of the compilation of " + execution);
        }
    }

    @Override
    public void onRunning(@NonNull FlowExecution execution) {
        try {
            Instant start = compileStarts.remove(execution.getOwner());
            if (start == null) {
                return;
            }
            WorkflowRun run = runOf(execution);
            if (run != null) {
                recordSpan(run, start, false);
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, e, () -> "Failed to record the compile span of " + execution);
        }
    }

    /**
     * Called when a run completes, before the run moves to its finalise phase. If its compilation did not finish,
     * records the compile span with an error status.
     */
    public void onRunCompleted(@NonNull Run<?, ?> run) {
        try {
            if (compileStarts.isEmpty() || !(run instanceof WorkflowRun workflowRun)) {
                return;
            }
            Instant start = compileStarts.remove(workflowRun.asFlowExecutionOwner());
            if (start != null) {
                recordSpan(run, start, true);
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, e, () -> "Failed to record the compile span of " + run);
        }
    }

    private void recordSpan(@NonNull Run<?, ?> run, @NonNull Instant start, boolean failed) {
        Span phaseSpan = otelTraceService.getSpan(run);
        List<String> libraries = PipelineLibraries.namesAndVersions(run);
        SpanBuilder spanBuilder = jenkinsControllerOpenTelemetry
                .getDefaultTracer()
                .spanBuilder(ExtendedJenkinsAttributes.JENKINS_PIPELINE_SPAN_COMPILE_NAME)
                .setParent(Context.root().with(phaseSpan))
                .setStartTimestamp(start);
        Span span = spanBuilder.startSpan();
        span.setAttribute(ExtendedJenkinsAttributes.JENKINS_PIPELINE_LIBRARIES, libraries);
        span.setAttribute(ExtendedJenkinsAttributes.JENKINS_PIPELINE_LIBRARIES_COUNT, (long) libraries.size());
        phaseSpan.setAttribute(ExtendedJenkinsAttributes.JENKINS_PIPELINE_LIBRARIES, libraries);
        if (failed) {
            span.setStatus(StatusCode.ERROR, "The pipeline failed before its first step");
        }
        span.end();
    }

    @CheckForNull
    private static WorkflowRun runOf(@NonNull FlowExecution execution) {
        try {
            Queue.Executable executable = execution.getOwner().getExecutable();
            return executable instanceof WorkflowRun workflowRun ? workflowRun : null;
        } catch (IOException e) {
            LOGGER.log(Level.FINE, e, () -> "No run for " + execution);
            return null;
        }
    }

    /**
     * @return the number of builds that are compiling, for the tests
     */
    @VisibleForTesting
    @Restricted(NoExternalUse.class)
    public int pendingBuilds() {
        return compileStarts.size();
    }
}
