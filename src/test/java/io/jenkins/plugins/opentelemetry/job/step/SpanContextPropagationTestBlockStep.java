/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.GeneralNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Set;

public class SpanContextPropagationTestBlockStep extends Step {

    @DataBoundConstructor
    public SpanContextPropagationTestBlockStep() {
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "spanContextPropagationTestBlockStep";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class);
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }

    public static class Execution extends GeneralNonBlockingStepExecution {
        private static final long serialVersionUID = 1L;

        protected Execution(StepContext context) {
            super(context);
        }

        @Override
        public boolean start() throws Exception {
            run(this::doStart);
            return false;
        }

        private void doStart() throws Exception {
            getContext()
                .newBodyInvoker()
                .withCallback(new MyBodyExecutionCallback()).start();
        }

        private class MyBodyExecutionCallback extends TailCall {
            private static final long serialVersionUID = 1;

            @Override
            protected void finished(StepContext context) throws Exception {
                context.get(TaskListener.class).getLogger().println("Finished");
            }
        }
    }
}
