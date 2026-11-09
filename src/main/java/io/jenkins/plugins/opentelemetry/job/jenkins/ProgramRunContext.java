/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.jenkins;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jenkinsci.plugins.workflow.cps.CpsVmThreadListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * The OpenTelemetry context of one run of the program of a pipeline thread on a CPS VM thread: the span of the block
 * that the program is in ({@link OtelTraceService#getProgramSpan}) is current, and follows the program as it moves to
 * new nodes. Groovy code of the pipeline, such as a trusted library that makes an HTTP call, then runs in the span of
 * its block.
 * <p>
 * The scopes stay in order on the thread: when a new node comes while a scope opened after this one is still open, such
 * as the one of {@link StepStartContextListener} while a block step starts its body, the span changes once that scope
 * has closed.
 */
final class ProgramRunContext implements CpsVmThreadListener.ProgramRun {
    private static final ThreadLocal<ProgramRunContext> CURRENT = new ThreadLocal<>();

    private final WorkflowRun run;
    private final ProgramRunContext outer;
    private final Thread thread;

    /** The context that this object made current, while {@link #scope} is open. */
    private Context context;

    private Scope scope;

    /** A node whose span waits until no scope opened after this one is open; {@code null} if none. */
    @CheckForNull
    private FlowNode pending;

    private ProgramRunContext(@NonNull WorkflowRun run) {
        this.run = run;
        this.outer = CURRENT.get();
        this.thread = Thread.currentThread();
    }

    /** Makes the span of the program at {@code head} current, until {@link #close()}. */
    @NonNull
    static ProgramRunContext open(@NonNull WorkflowRun run, @CheckForNull FlowNode head) {
        ProgramRunContext programRun = new ProgramRunContext(run);
        CURRENT.set(programRun);
        programRun.moveTo(head);
        return programRun;
    }

    /** The program run in progress on this thread, if any. */
    @CheckForNull
    static ProgramRunContext current() {
        return CURRENT.get();
    }

    @Override
    public void onNewHead(@NonNull FlowNode head) {
        moveTo(head);
    }

    /** Applies the span of a node that came while a scope opened after this one was open. */
    void resume() {
        if (pending != null && isInnermost()) {
            moveTo(pending);
        }
    }

    @Override
    public void close() {
        pending = null;
        if (scope != null) {
            scope.close();
            scope = null;
            context = null;
        }
        if (CURRENT.get() == this) {
            if (outer == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(outer);
            }
        }
    }

    private void moveTo(@CheckForNull FlowNode head) {
        if (Thread.currentThread() != thread) {
            return;
        }
        if (scope != null && !isInnermost()) {
            pending = head;
            return;
        }
        pending = null;
        OtelTraceService otelTraceService = OtelTraceService.get();
        Span span = head == null ? otelTraceService.getSpan(run) : otelTraceService.getProgramSpan(run, head);
        if (scope != null && Span.fromContext(context) == span) {
            return;
        }
        if (scope != null) {
            scope.close();
        }
        context = Context.current().with(span);
        scope = context.makeCurrent();
    }

    private boolean isInnermost() {
        return Context.current() == context;
    }
}
