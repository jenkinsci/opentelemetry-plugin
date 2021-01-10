package io.jenkins.plugins.opentelemetry;

import static com.google.common.base.Verify.*;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.logging.Logger;

public abstract class OpenTelemetryContextAwareRunListener<R extends Run> extends RunListener<Run> {

    private final static Logger LOGGER = Logger.getLogger(OpenTelemetryContextAwareRunListener.class.getName());

    private OpenTelemetryTracerService openTelemetryTracerService;
    private Tracer tracer;
    private OpenTelemetry openTelemetry;

    @Inject
    public final void setOpenTelemetryTracerService(OpenTelemetryTracerService openTelemetryTracerService) {
        this.openTelemetryTracerService = openTelemetryTracerService;
        this.openTelemetryTracerService = openTelemetryTracerService;
        this.openTelemetry = openTelemetryTracerService.getOpenTelemetry();
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
    protected Scope setupContext(@Nonnull Run run) {
        Span span = this.openTelemetryTracerService.getSpan(run);
        if (span == null) {
            return null;
        } else {
            Scope scope = span.makeCurrent();
            return scope;
        }
    }

    public OpenTelemetryTracerService getOpenTelemetryTracerService() {
        return openTelemetryTracerService;
    }

    public Tracer getTracer() {
        return tracer;
    }

    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }
}
