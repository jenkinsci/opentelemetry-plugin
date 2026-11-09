/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.jenkins;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * Makes the span of a pipeline step current while the step starts, on the thread that starts it, and only then: that
 * thread runs other steps, of this build and of others, afterward. Code of the step's start then creates its spans
 * under the step: spans of HTTP client instrumentation, spans of the step itself, and spans of the work that a
 * {@link org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution} submits, which takes the context
 * current when it is submitted. An atomic step has a span of its own; a block step without one, like
 * {@code withCredentials}, starts in the span that encloses it. Once the start returns, the program goes on in the span
 * of its block again ({@link ProgramRunContext}).
 */
@Extension
public class StepStartContextListener implements StepListener {
    private static final Logger LOGGER = Logger.getLogger(StepStartContextListener.class.getName());

    @Override
    public void notifyOfNewStep(@NonNull Step step, @NonNull StepContext context) {}

    @CheckForNull
    @Override
    public AutoCloseable aroundStart(@NonNull Step step, @NonNull StepContext context) {
        try {
            Run<?, ?> run = context.get(Run.class);
            FlowNode node = context.get(FlowNode.class);
            if (!(run instanceof WorkflowRun) || node == null) {
                return null;
            }
            Span span = OtelTraceService.get().getSpan(run, node);
            if (!span.getSpanContext().isValid()) {
                return null;
            }
            Scope scope = span.makeCurrent();
            ProgramRunContext programRun = ProgramRunContext.current();
            return () -> {
                scope.close();
                if (programRun != null) {
                    programRun.resume();
                }
            };
        } catch (IOException | RuntimeException e) {
            LOGGER.log(
                    Level.FINE,
                    e,
                    () -> "No span for the start of " + step.getDescriptor().getFunctionName());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
