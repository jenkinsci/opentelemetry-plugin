/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.steps;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.plugins.opentelemetry.steps.model.SpanAttribute;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.util.List;

public class CreateSpanExecution extends AbstractStepExecutionImpl {

    @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
    private transient final String name;

    @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
    private transient final List<SpanAttribute> attributes;

    CreateSpanExecution(String name, List<SpanAttribute> attributes, StepContext context) {
        super(context);
        this.name = name;
        this.attributes = attributes;
    }

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        context.newBodyInvoker()
            .withCallback(new Callback())
            .start();
        return false;   // execution is asynchronous
    }

    @Override public void onResume() {}

    private static class Callback extends BodyExecutionCallback {

        Callback() { }

        @Override
        public void onSuccess(StepContext context, Object result) {
            context.onSuccess(result);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            context.onFailure(t);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
