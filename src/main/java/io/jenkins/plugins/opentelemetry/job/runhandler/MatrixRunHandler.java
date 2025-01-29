/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.runhandler;

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
import jenkins.YesNoMaybe;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class MatrixRunHandler implements RunHandler {

    private boolean expandJobName;

    public MatrixRunHandler() throws ClassNotFoundException {
        // verify the class is available to force the contract `@Extension(optional = true)`
        Class.forName(MatrixRun.class.getName());
    }

    @Override
    public boolean canCreateSpanBuilder(@NonNull Run<?, ?> run) {
        return run instanceof MatrixRun || run instanceof MatrixBuild;
    }

    @NonNull
    @Override
    public SpanBuilder createSpanBuilder(@NonNull Run<?, ?> run, @NonNull Tracer tracer) {
        if (run instanceof MatrixRun matrixRun) {
            MatrixConfiguration matrixConfiguration = matrixRun.getParent();

            MatrixProject matrixProject = matrixConfiguration.getParent();
            String spanName = expandJobName ? run.getParent().getFullName() : matrixProject.getFullName() + "/execution";
            SpanBuilder spanBuilder = tracer.spanBuilder(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + spanName);
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
            return tracer.spanBuilder(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + matrixBuild.getParent().getFullName());
        } else {
            throw new IllegalStateException("Unsupported run type " + run);
        }
    }

    @Override
    public void configure(ConfigProperties config) {
        expandJobName = Boolean.TRUE.equals(config.getBoolean("otel.instrumentation.jenkins.job.matrix.expand.job.name"));
    }
}
