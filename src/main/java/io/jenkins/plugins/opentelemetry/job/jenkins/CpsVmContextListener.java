/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.jenkins;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Queue;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsVmThreadListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * Sets the OpenTelemetry context of the work that a CPS VM thread does for a pipeline build, and only for that work: the
 * CPS VM runs the work of all builds on one pool of threads. Each task of the build, such as the notifications of
 * asynchronous graph listeners, runs in the span of the build's run phase and in nothing else: not in a context left on
 * the thread, and not in the context of the thread that submitted the task, which instrumentation of executors can
 * carry over and which can be of another build. Each run of the build's program runs in the span of the block that the
 * program is in ({@link ProgramRunContext}). The CPS VM thread closes both before it does other work.
 */
@Extension
public class CpsVmContextListener implements CpsVmThreadListener {
    private static final Logger LOGGER = Logger.getLogger(CpsVmContextListener.class.getName());

    @CheckForNull
    @Override
    public AutoCloseable aroundTask(@NonNull CpsFlowExecution execution) {
        WorkflowRun run = run(execution);
        if (run == null) {
            return null;
        }
        Span span = OtelTraceService.get().getSpan(run);
        return Context.root().with(span).makeCurrent();
    }

    @CheckForNull
    @Override
    public ProgramRun aroundProgramRun(@NonNull CpsFlowExecution execution, @CheckForNull FlowNode head) {
        WorkflowRun run = run(execution);
        return run == null ? null : ProgramRunContext.open(run, head);
    }

    @CheckForNull
    private static WorkflowRun run(@NonNull CpsFlowExecution execution) {
        try {
            Queue.Executable executable = execution.getOwner().getExecutable();
            return executable instanceof WorkflowRun ? (WorkflowRun) executable : null;
        } catch (IOException e) {
            LOGGER.log(Level.FINE, e, () -> "No build for " + execution);
            return null;
        }
    }
}
