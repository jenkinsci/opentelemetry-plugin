/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.ClassLoaderSanityThreadFactory;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

@Extension
public class StepExecutionInstrumentationInitializer implements OpenTelemetryLifecycleListener {

    static final Logger logger = Logger.getLogger(StepExecutionInstrumentationInitializer.class.getName());

    @Override
    public void afterConfiguration(@NonNull ConfigProperties configProperties) {
        try {
            logger.log(
                    Level.FINE, () -> "Instrumenting " + SynchronousNonBlockingStepExecution.class.getName() + "...");
            Class<SynchronousNonBlockingStepExecution> synchronousNonBlockingStepExecutionClass =
                    SynchronousNonBlockingStepExecution.class;
            Arrays.stream(synchronousNonBlockingStepExecutionClass.getDeclaredFields())
                    .forEach(field -> logger.log(Level.FINE, () -> "Field: " + field.getName()));
            Field executorServiceField = synchronousNonBlockingStepExecutionClass.getDeclaredField("executorService");
            executorServiceField.setAccessible(true);
            ExecutorService executorService = (ExecutorService) Optional.ofNullable(executorServiceField.get(null))
                    .orElseGet(() -> Executors.newCachedThreadPool(new NamingThreadFactory(
                            new ClassLoaderSanityThreadFactory(new DaemonThreadFactory()),
                            "org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution")));
            ExecutorService instrumentedExecutorService = Context.taskWrapping(executorService);
            executorServiceField.set(null, instrumentedExecutorService);

            // org.jenkinsci.plugins.workflow.cps.CpsThreadGroup.runner
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
