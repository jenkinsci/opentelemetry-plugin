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
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jenkins.YesNoMaybe;

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
    public boolean matches(@NonNull Run<?, ?> run) {
        return run instanceof MatrixRun || run instanceof MatrixBuild;
    }

    @NonNull
    @Override
    public String getPipelineShortName(@NonNull Run<?, ?> run) {
        if (run instanceof MatrixRun matrixRun) {
            MatrixConfiguration matrixConfiguration = matrixRun.getParent();
            MatrixProject matrixProject = matrixConfiguration.getParent();
            return expandJobName ? run.getParent().getFullName() : matrixProject.getFullName() + "/execution";
        } else if (run instanceof MatrixBuild matrixBuild) {
            return matrixBuild.getParent().getFullName();
        } else {
            throw new IllegalStateException("Unsupported run type " + run);
        }
    }

    @Override
    public void enrichPipelineRunSpan(@NonNull Run<?, ?> run, @NonNull SpanBuilder spanBuilder) {
        if (run instanceof MatrixRun matrixRun) {

            Combination combination = matrixRun.getParent().getCombination();
            List<String> axisNames = new ArrayList<>();
            List<String> axisValues = new ArrayList<>();

            combination.forEach((key, value) -> {
                axisNames.add(key);
                axisValues.add(value);
            });
            spanBuilder.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_AXIS_NAMES, axisNames);
            spanBuilder.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_AXIS_VALUES, axisValues);
        }
    }

    @Override
    public void configure(ConfigProperties config) {
        expandJobName = Boolean.TRUE.equals(config.getBoolean("otel.instrumentation.jenkins.job.matrix.expand.job.name"));
    }
}
