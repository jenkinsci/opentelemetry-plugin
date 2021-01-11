package io.jenkins.plugins.opentelemetry.trace.context;

import static com.google.common.base.Verify.*;

import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import io.jenkins.plugins.opentelemetry.OtelTracerService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.logging.Logger;

public abstract class OtelContextAwareAbstractRunListener<R extends Run> extends RunListener<Run> {

    private final static Logger LOGGER = Logger.getLogger(OtelContextAwareAbstractRunListener.class.getName());

    private OtelTracerService otelTracerService;
    private Tracer tracer;
    private OpenTelemetry openTelemetry;

    @Inject
    public final void setOpenTelemetryTracerService(OtelTracerService otelTracerService) {
        this.otelTracerService = otelTracerService;
        this.otelTracerService = otelTracerService;
        this.openTelemetry = otelTracerService.getOpenTelemetry();
        this.tracer = this.openTelemetry.getTracer("jenkins");
    }

    @Override
    public final void onCompleted(Run run, @NonNull TaskListener listener) {
        try (Scope ignored = setupContext(run)) {
            verifyNotNull(ignored, "No span found for %s", run);
            this._onCompleted(run, listener);
        }
    }

    public void _onCompleted(Run run, @NonNull TaskListener listener) {
    }

    @Override
    public final void onFinalized(Run run) {
        try (Scope ignored = setupContext(run)) {
            verifyNotNull(ignored, "No span found for %s", run);
            this._onFinalized(run);
        }
    }


    public void _onFinalized(Run run) {
    }

    @Override
    public final void onInitialize(Run run) {
        Scope ignored = setupContext(run);
        verify(ignored == null, "No span should be defined for %s");
        this._onInitialize(run);
    }

    public void _onInitialize(Run run) {
    }

    @Override
    public final void onStarted(Run run, TaskListener listener) {
        try (Scope ignored = setupContext(run)) {
            verifyNotNull(ignored, "No span found for %s", run);
            this._onStarted(run, listener);
        }
    }

    public void _onStarted(Run run, TaskListener listener) {
    }

    @Override
    public final Environment setUpEnvironment(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        try (Scope ignored = setupContext(build)) {
            verifyNotNull(ignored, "No span found for %s", build);
            return this._setUpEnvironment(build, launcher, listener);
        }
    }

    public Environment _setUpEnvironment(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        return new Environment() {
        };
    }

    @Override
    public final void onDeleted(Run run) {
        try (Scope ignored = setupContext(run)) {
            verifyNotNull(ignored, "No span found for %s", run);
            this._onDeleted(run);
        }
    }

    public void _onDeleted(Run run) {
    }


    /**
     * @param run
     * @return {@code null} if no {@link Span} has been created for the given {@link Run}
     */
    @CheckForNull
    @MustBeClosed
    protected Scope setupContext(@Nonnull Run run) {
        Span span = this.otelTracerService.getSpan(run);
        if (span == null) {
            return null;
        } else {
            Scope scope = span.makeCurrent();
            Context.current().with(RunContextKey.KEY, run);
            return scope;
        }
    }

    public OtelTracerService getOpenTelemetryTracerService() {
        return otelTracerService;
    }

    public Tracer getTracer() {
        return tracer;
    }

    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }
}
