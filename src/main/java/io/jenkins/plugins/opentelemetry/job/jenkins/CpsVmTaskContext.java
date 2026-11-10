/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.jenkins;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * The OpenTelemetry context of one task that a CPS VM thread runs for a pipeline build: the span of the build's
 * current phase ({@link OtelTraceService#getSpan(Run)}) is current, and follows the build when it moves to its next
 * phase during the task. The task that completes a build runs the listeners of its completion and of its finalization,
 * and this plugin's listeners among them end the phase "Run", start the phase "Finalise", then end it. The work after
 * them in the task, such as the listeners of other plugins and the deletion of the build's stashes, runs in "Finalise"
 * while the build is in it, and in no span once the build has ended: not in a phase that has ended.
 * <p>
 * The scopes stay in order on the thread: the span changes only while the scope of this object is the innermost one
 * ({@link ContextOwner}).
 */
public final class CpsVmTaskContext implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(CpsVmTaskContext.class.getName());
    private static final ThreadLocal<CpsVmTaskContext> CURRENT = new ThreadLocal<>();

    private final WorkflowRun run;

    @CheckForNull
    private final CpsVmTaskContext outer;

    /** The span of the context that this object made current, while {@link #scope} is open. */
    private Span span;

    private Scope scope;

    private CpsVmTaskContext(@NonNull WorkflowRun run) {
        this.run = run;
        this.outer = CURRENT.get();
    }

    /** Makes the span of the current phase of {@code run} current, until {@link #close()}. */
    @NonNull
    static CpsVmTaskContext open(@NonNull WorkflowRun run) {
        CpsVmTaskContext task = new CpsVmTaskContext(run);
        task.span = OtelTraceService.get().getSpan(run);
        task.scope = ContextOwner.with(Context.root(), task.span, task).makeCurrent();
        CURRENT.set(task);
        return task;
    }

    /**
     * To call once the scopes of a listener that can move {@code run} to its next phase are closed: if this thread runs
     * a CPS VM task of {@code run}, the rest of the task runs in the span of the phase that the build is in now.
     */
    public static void followPhase(@NonNull Run<?, ?> run) {
        CpsVmTaskContext task = CURRENT.get();
        if (task == null || task.run != run || task.scope == null || task.span == null) {
            return;
        }
        Span span = OtelTraceService.get().getSpan(run);
        if (ContextOwner.sameSpan(task.span, span)) {
            return;
        }
        if (!ContextOwner.isCurrent(task, task.span)) {
            LOGGER.log(
                    Level.FINE,
                    () -> run.getFullDisplayName()
                            + " - phase changed while a scope opened in the CPS VM task is open, the task stays in "
                            + "the span of its previous phase");
            return;
        }
        task.scope.close();
        task.span = span;
        task.scope = ContextOwner.with(Context.root(), span, task).makeCurrent();
    }

    @Override
    public void close() {
        if (scope != null) {
            scope.close();
            scope = null;
            span = null;
        }
        if (CURRENT.get() == this) {
            if (outer == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(outer);
            }
        }
    }
}
