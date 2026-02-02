/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import hudson.Extension;
import hudson.model.TaskListener;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

public class SpanContextPropagationSynchronousNonBlockingTestStep extends Step implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger logger =
            Logger.getLogger(SpanContextPropagationSynchronousNonBlockingTestStep.class.getName());
    transient OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    transient Tracer tracer = openTelemetry.getTracer("io.jenkins.opentelemetry.test");

    @DataBoundConstructor
    public SpanContextPropagationSynchronousNonBlockingTestStep() {}

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new StepExecution(context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }

        @Override
        public String getFunctionName() {
            return "spanContextPropagationSynchronousNonBlockingTestStep";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "spanContextPropagationSynchronousNonBlockingTestStep";
        }
    }

    private class StepExecution extends SynchronousNonBlockingStepExecution<Void> {
        public StepExecution(StepContext context) {
            super(context);
        }

        @Override
        protected Void run() throws Exception {

            Span span = tracer.spanBuilder("SpanContextPropagationSynchronousNonBlockingTestStep.execution")
                    .startSpan();
            try (Scope ctx = span.makeCurrent()) {
                TaskListener taskListener = Objects.requireNonNull(getContext().get(TaskListener.class));
                taskListener.getLogger().println(getClass().getName());
            } finally {
                span.end();
            }
            return null;
        }

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
