/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import hudson.Extension;
import java.io.Serial;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Test step that blocks its build until the test calls {@link #release(String)}, like the {@code semaphore} step of
 * the workflow-support tests jar. The CPS VM task that starts it returns its thread to the pool while the step's span
 * is still current.
 */
public class ContextLeakBlockTestStep extends Step {
    private static final Map<String, StepContext> STARTED = new ConcurrentHashMap<>();

    private final String id;

    @DataBoundConstructor
    public ContextLeakBlockTestStep(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(context, id);
    }

    /** Waits until a build has started the step with this id. */
    public static void waitForStart(String id) throws InterruptedException {
        for (int i = 0; i < 600 && !STARTED.containsKey(id); i++) {
            Thread.sleep(100);
        }
        if (!STARTED.containsKey(id)) {
            throw new AssertionError("No build started contextLeakBlockTestStep '" + id + "' in 60 s");
        }
    }

    /** Completes the step with this id: its build goes on. */
    public static void release(String id) {
        Objects.requireNonNull(STARTED.remove(id), id).onSuccess(null);
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
        public boolean start() {
            STARTED.put(id, getContext());
            return false;
        }

        @Override
        public void stop(@Nonnull Throwable cause) {
            STARTED.remove(id);
            getContext().onFailure(cause);
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of();
        }

        @Override
        public String getFunctionName() {
            return "contextLeakBlockTestStep";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "contextLeakBlockTestStep";
        }
    }
}
