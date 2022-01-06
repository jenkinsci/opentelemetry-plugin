package io.jenkins.plugins.opentelemetry.job;

import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import hudson.tasks.BuildStep;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.RunContextKey;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.ScmContextKey;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Verify.verifyNotNull;
import static io.jenkins.plugins.opentelemetry.OtelUtils.JENKINS_CORE;

public class MonitoringSCMListener extends SCMListener {

    protected static final Logger LOGGER = Logger.getLogger(MonitoringRunListener.class.getName());

    private OtelTraceService otelTraceService;
    private Tracer tracer;

    @Override
    public void onCheckout(Run<?,?> build, SCM scm, FilePath workspace, TaskListener listener, @CheckForNull File changelogFile, @CheckForNull SCMRevisionState pollingBaseline) throws Exception {

        String stepName = JenkinsOpenTelemetryPluginConfiguration.get().findSymbolOrDefault(scm.getClass().getSimpleName(), scm);

        try (Scope ignored = setupContext(build, scm)) {
            verifyNotNull(ignored, "%s - No span found for step %s", build, scm);

            SpanBuilder spanBuilder = getTracer().spanBuilder(stepName);
            JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepName, scm);

            final String jenkinsVersion = OtelUtils.getJenkinsVersion();
            spanBuilder
                .setParent(Context.current())
                .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, stepName)
                .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.isUnknown() ? JENKINS_CORE : stepPlugin.getName())
                .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.isUnknown() ? jenkinsVersion : stepPlugin.getVersion());

            // TODO: enriche span with the SCM attributes (similar to io.jenkins.plugins.opentelemetry.job.step.GitStepHandler.createSpanBuilder)
            Span atomicScmSpan = spanBuilder.startSpan();
            LOGGER.log(Level.FINE, () -> build.getFullDisplayName() + " - > " + stepName + " - begin " + OtelUtils.toDebugString(atomicScmSpan));

            getTracerService().putSpan(build, atomicScmSpan);
        }
    }

    /**
     * @return {@code null} if no {@link Span} has been created for the {@link AbstractBuild} of the given {@link BuildStep}
     */
    @javax.annotation.CheckForNull
    @MustBeClosed
    protected Scope setupContext(Run<?,?> build, @Nonnull SCM scm) {
        build = verifyNotNull(build, "%s No build found for step %s", build, scm);
        Span span = this.otelTraceService.getSpan(build, scm);

        Scope scope = span.makeCurrent();
        Context.current().with(RunContextKey.KEY, build).with(ScmContextKey.KEY, scm);
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
