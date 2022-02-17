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
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jenkins.YesNoMaybe;

import javax.annotation.Nonnull;
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
    public boolean canCreateSpanBuilder(@Nonnull Run run) {
        return run instanceof MatrixRun || run instanceof MatrixBuild;
    }

    @Nonnull
    @Override
    public SpanBuilder createSpanBuilder(@Nonnull Run run, @Nonnull Tracer tracer) {
        if (run instanceof MatrixRun) {
            MatrixRun matrixRun = (MatrixRun) run;
            MatrixConfiguration matrixConfiguration = matrixRun.getParent();

            MatrixProject matrixProject = matrixConfiguration.getParent();
            String spanName = expandJobName ? run.getParent().getFullName() : matrixProject.getFullName() + "/execution";
            SpanBuilder spanBuilder = tracer.spanBuilder("BUILD" + spanName);
            Combination combination = matrixConfiguration.getCombination();
            List<String> axisNames = new ArrayList<>();
            List<String> axisValues = new ArrayList<>();

            combination.entrySet().stream().forEach(entry -> {
                    axisNames.add(entry.getKey());
                    axisValues.add(entry.getValue());
                }
            );
            spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_AXIS_NAMES, axisNames);
            spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_AXIS_VALUES, axisValues);

            return spanBuilder;
        } else if (run instanceof MatrixBuild) {
            MatrixBuild matrixBuild = (MatrixBuild) run;
            return tracer.spanBuilder("BUILD" + matrixBuild.getParent().getFullName());
        } else {
            throw new IllegalStateException("Unsupported run type " + run);
        }
    }

    @Override
    public void configure(ConfigProperties config) {
        expandJobName = Boolean.TRUE.equals(config.getBoolean("otel.instrumentation.jenkins.job.matrix.expand.job.name"));
    }
}
