/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.BuildStepListener;
import hudson.tasks.BuildStep;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jenkins.YesNoMaybe;

import javax.inject.Inject;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Verify.verifyNotNull;
import static io.jenkins.plugins.opentelemetry.OtelUtils.JENKINS_CORE;

@Extension(dynamicLoadable = YesNoMaybe.YES)
public class MonitoringBuildStepListener extends BuildStepListener  {

    protected static final Logger LOGGER = Logger.getLogger(MonitoringRunListener.class.getName());

    private OtelTraceService otelTraceService;
    private Tracer tracer;

    /** {@inheritDoc} */
    @Override
    public void started(AbstractBuild build, BuildStep buildStep, BuildListener listener) {
        String stepName = JenkinsOpenTelemetryPluginConfiguration.get().findSymbolOrDefault(buildStep.getClass().getSimpleName(), buildStep);

        try (Scope ignored = setupContext(build, buildStep)) {
            verifyNotNull(ignored, "%s - No span found for step %s", build, buildStep);

            SpanBuilder spanBuilder = getTracer().spanBuilder(stepName);
            JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepName, buildStep);

            final String jenkinsVersion = OtelUtils.getJenkinsVersion();
            spanBuilder
                .setParent(Context.current())
                .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_NAME, stepName)
                .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.isUnknown() ? JENKINS_CORE : stepPlugin.getName())
                .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.isUnknown() ? jenkinsVersion : stepPlugin.getVersion());

            Span atomicStepSpan = spanBuilder.startSpan();
            LOGGER.log(Level.FINE, () -> build.getFullDisplayName() + " - > " + stepName + " - begin " + OtelUtils.toDebugString(atomicStepSpan));

            getTracerService().putSpan(build, buildStep, atomicStepSpan);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void finished(AbstractBuild build, BuildStep buildStep, BuildListener listener, boolean canContinue) {
        String stepName = JenkinsOpenTelemetryPluginConfiguration.get().findSymbolOrDefault(buildStep.getClass().getSimpleName(), buildStep);

        try (Scope ignored = setupContext(build, buildStep)) {
            verifyNotNull(ignored, "%s - No span found for step %s", build, buildStep);

            Span span = getTracerService().getSpan(build, buildStep);
            if (canContinue) {
                span.setStatus(StatusCode.OK);
            } else {
                // Create a synthetic error with the buildStep details.
                JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepName, buildStep);
                if (stepPlugin.isUnknown()) {
                    final String jenkinsVersion = OtelUtils.getJenkinsVersion();
                    stepPlugin = new JenkinsOpenTelemetryPluginConfiguration.StepPlugin(JENKINS_CORE, jenkinsVersion);
                }
                span.recordException(new AbortException("StepName: " + stepName + ", " + stepPlugin));
                span.setStatus(StatusCode.ERROR, "Build step failed");
            }

            span.end();
            getTracerService().removeBuildStepSpan(build, buildStep, span);
            LOGGER.log(Level.FINE, () -> build.getFullDisplayName() + " - < " + stepName + " - end " + OtelUtils.toDebugString(span));
        }
    }

    /**
     * @return {@code null} if no {@link Span} has been created for the {@link AbstractBuild} of the given {@link BuildStep}
     */
    @MustBeClosed
    @NonNull
    protected Scope setupContext(AbstractBuild<?, ?> build, @NonNull BuildStep buildStep) {
        verifyNotNull(build, "%s No build found for step %s", build, buildStep);
        Span span = this.otelTraceService.getSpan(build, buildStep);
        return span.makeCurrent();
    }

    @Inject
    public final void setOpenTelemetryTracerService(@NonNull OtelTraceService otelTraceService) {
        this.otelTraceService = otelTraceService;
    }

    @NonNull
    public OtelTraceService getTracerService() {
        return otelTraceService;
    }

    @NonNull
    public Tracer getTracer() {
        return Objects.requireNonNull(tracer, "Null Tracer, #postConstruct has not bee invoked on listener.");
    }

    @Override
    public String toString() {
        return "MonitoringBuildStepListener{}";
    }

    @Inject
    public void setTracer(JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry) {
        this.tracer = jenkinsControllerOpenTelemetry.getDefaultTracer();
    }
}
