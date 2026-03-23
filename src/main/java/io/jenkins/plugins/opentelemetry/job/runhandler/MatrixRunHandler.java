/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.runhandler;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.matrix.Combination;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixRun;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.List;
import jenkins.YesNoMaybe;

@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
/** Run handler for Jenkins matrix jobs and matrix configuration runs. */
public class MatrixRunHandler implements RunHandler {

    private boolean expandJobName;

    /**
     * Creates the handler and verifies matrix classes are present for optional plugin loading.
     *
     * @throws ClassNotFoundException if matrix plugin classes are unavailable
     */
    public MatrixRunHandler() throws ClassNotFoundException {
        // verify the class is available to force the contract `@Extension(optional = true)`
        Class.forName(MatrixRun.class.getName());
    }

    /**
     * Indicates whether this handler supports the given run type.
     *
     * @param run Jenkins run instance
     * @return {@code true} for matrix build and matrix run types
     */
    @Override
    public boolean canCreateSpanBuilder(@NonNull Run<?, ?> run) {
        return run instanceof MatrixRun || run instanceof MatrixBuild;
    }

    /**
     * Creates a span builder populated with matrix-specific attributes.
     *
     * @param run Jenkins run instance
     * @param tracer tracer used to create the span builder
     * @return configured span builder
     */
    @NonNull
    @Override
    public SpanBuilder createSpanBuilder(@NonNull Run<?, ?> run, @NonNull Tracer tracer) {
        if (run instanceof MatrixRun matrixRun) {
            MatrixConfiguration matrixConfiguration = matrixRun.getParent();

            MatrixProject matrixProject = matrixConfiguration.getParent();
            String spanName =
                    expandJobName ? run.getParent().getFullName() : matrixProject.getFullName() + "/execution";
            SpanBuilder spanBuilder =
                    tracer.spanBuilder(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + spanName);
            Combination combination = matrixConfiguration.getCombination();
            List<String> axisNames = new ArrayList<>();
            List<String> axisValues = new ArrayList<>();

            combination.forEach((key, value) -> {
                axisNames.add(key);
                axisValues.add(value);
            });
            spanBuilder.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_AXIS_NAMES, axisNames);
            spanBuilder.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_AXIS_VALUES, axisValues);

            return spanBuilder;
        } else if (run instanceof MatrixBuild matrixBuild) {
            return tracer.spanBuilder(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX
                    + matrixBuild.getParent().getFullName());
        } else {
            throw new IllegalStateException("Unsupported run type " + run);
        }
    }

    /**
     * Applies runtime configuration controlling matrix job span naming.
     *
     * @param config OpenTelemetry configuration properties
     */
    @Override
    public void configure(ConfigProperties config) {
        expandJobName =
                Boolean.TRUE.equals(config.getBoolean("otel.instrumentation.jenkins.job.matrix.expand.job.name"));
    }
}
