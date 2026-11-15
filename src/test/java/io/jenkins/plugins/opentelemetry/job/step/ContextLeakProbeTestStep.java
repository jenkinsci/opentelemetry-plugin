/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.io.Serial;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Test block step that records the OpenTelemetry context its {@code start()} runs in. The plugin creates no span for
 * such a block step, like {@code withCredentials}, where a credentials provider that calls a remote store makes its
 * HTTP calls: its start must run in the span that encloses it, of its own build.
 */
public class ContextLeakProbeTestStep extends Step {
    public static final List<Observation> OBSERVATIONS = new CopyOnWriteArrayList<>();

    private final String id;

    @DataBoundConstructor
    public ContextLeakProbeTestStep(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(context, id);
    }

    /**
     * @param run          the build that ran the step
     * @param buildTraceId the trace id of that build
     * @param current      the span context current when {@code start()} began
     * @param recording    whether that span was still recording (not ended)
     */
    public record Observation(
            String run, String id, String thread, String buildTraceId, SpanContext current, boolean recording) {

        public boolean inOwnBuild() {
            return current.isValid() && recording && current.getTraceId().equals(buildTraceId);
        }
    }

    private static class Execution extends StepExecution {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String id;

        Execution(StepContext context, String id) {
            super(context);
            this.id = id;
        }

        @Override
        public boolean start() throws Exception {
            Span current = Span.current();
            Run<?, ?> run = Objects.requireNonNull(getContext().get(Run.class));
            String buildTraceId = ExtensionList.lookupSingleton(OtelTraceService.class)
                    .getPipelineRootSpan(run)
                    .getSpanContext()
                    .getTraceId();
            OBSERVATIONS.add(new Observation(
                    run.getFullDisplayName(),
                    id,
                    Thread.currentThread().getName(),
                    buildTraceId,
                    current.getSpanContext(),
                    current.isRecording()));
            getContext()
                    .newBodyInvoker()
                    .withCallback(BodyExecutionCallback.wrap(getContext()))
                    .start();
            return false;
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class);
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public String getFunctionName() {
            return "contextLeakProbeTestStep";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "contextLeakProbeTestStep";
        }
    }
}
