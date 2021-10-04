/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.base.Strings;
import com.google.errorprone.annotations.MustBeClosed;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.BuildStepListener;
import hudson.model.Node;
import hudson.tasks.BuildStep;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.BuildStepContextKey;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.RunContextKey;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Verify.verifyNotNull;

@Extension
public class MonitoringBuildStepListener extends BuildStepListener {

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

            // TODO: For core buildSteps the stepPlugin is unknown, we might need to decorate those values with core and version: service-version
            spanBuilder
                .setParent(Context.current())
                .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, stepName)
                .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());

            Span atomicStepSpan = spanBuilder.startSpan();
            LOGGER.log(Level.FINE, () -> build.getFullDisplayName() + " - > " + stepName + " - begin " + OtelUtils.toDebugString(atomicStepSpan));

            getTracerService().putSpan(build, atomicStepSpan);
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
                // TODO: fetch the error for the given buildStep if possible
                // then span.recordException
                span.setStatus(StatusCode.ERROR, "Build step failed");
            }

            Node node = build.getBuiltOn();
            if (node != null) {
                span.setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_AGENT_LABEL, Strings.emptyToNull(node.getLabelString()));
                span.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_AGENT_ID, node.getNodeName());
                span.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_AGENT_NAME, node.getDisplayName());
            }
            span.end();
            getTracerService().removeBuildStepSpan(build, buildStep, span);
            LOGGER.log(Level.FINE, () -> build.getFullDisplayName() + " - < " + stepName + " - end " + OtelUtils.toDebugString(span));
        }
    }

    /**
     * @return {@code null} if no {@link Span} has been created for the {@link AbstractBuild} of the given {@link BuildStep}
     */
    @CheckForNull
    @MustBeClosed
    protected Scope setupContext(AbstractBuild build, @Nonnull BuildStep buildStep) {
        build = verifyNotNull(build, "%s No build found for step %s", build, buildStep);
        Span span = this.otelTraceService.getSpan(build, buildStep);

        Scope scope = span.makeCurrent();
        Context.current().with(RunContextKey.KEY, build).with(BuildStepContextKey.KEY, buildStep);
        return scope;
    }

    @Inject
    public final void setOpenTelemetryTracerService(@Nonnull OtelTraceService otelTraceService) {
        this.otelTraceService = otelTraceService;
        this.tracer = this.otelTraceService.getTracer();
    }

    @Nonnull
    public OtelTraceService getTracerService() {
        return otelTraceService;
    }

    @Nonnull
    public Tracer getTracer() {
        return tracer;
    }

    @Override
    public String toString() {
        return "MonitoringBuildStepListener{}";
    }
}
