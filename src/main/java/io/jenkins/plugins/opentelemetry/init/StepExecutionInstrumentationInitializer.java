/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import io.opentelemetry.context.Context;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

@Extension
public class StepExecutionInstrumentationInitializer
        implements SynchronousNonBlockingStepExecution.ExecutorServiceAugmentor {

    static final Logger logger = Logger.getLogger(StepExecutionInstrumentationInitializer.class.getName());

    public ExecutorService augment(ExecutorService executorService) {
        logger.log(Level.FINE, () -> "Instrumenting " + SynchronousNonBlockingStepExecution.class.getName() + "...");
        return Context.taskWrapping(executorService);
    }
}
