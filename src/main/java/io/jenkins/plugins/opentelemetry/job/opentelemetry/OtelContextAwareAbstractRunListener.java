/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.opentelemetry;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * {@link RunListener} that setups the OpenTelemetry {@link io.opentelemetry.context.Context}
 * with the current {@link Span}.
 */
public abstract class OtelContextAwareAbstractRunListener extends RunListener<Run> implements OtelComponent {

    private final static Logger LOGGER = Logger.getLogger(OtelContextAwareAbstractRunListener.class.getName());

    private OtelTraceService otelTraceService;
    private Tracer tracer;

    @Inject
    public final void setOpenTelemetryTracerService(@Nonnull OtelTraceService otelTraceService) {
        this.otelTraceService = otelTraceService;
    }

    @Override
    public void afterSdkInitialized(Meter meter, io.opentelemetry.api.logs.Logger otelLogger, EventEmitter eventEmitter, Tracer tracer, ConfigProperties configProperties) {
        this.tracer = tracer;
    }

    @Override
    public final void onCompleted(@NonNull Run run, @NonNull TaskListener listener) {
        try (Scope scope = getTraceService().setupContext(run)) {
            this._onCompleted(run, listener);
        }
    }

    public void _onCompleted(@NonNull Run run, @NonNull TaskListener listener) {
    }

    @Override
    public final void onFinalized(@NonNull Run run) {
        try (Scope scope = getTraceService().setupContext(run)) {
            this._onFinalized(run);
        }
    }


    public void _onFinalized(Run run) {
    }

    @Override
    public final void onInitialize(@NonNull Run run) {
        this._onInitialize(run);
    }

    public void _onInitialize(@NonNull Run run) {
    }

    @Override
    public final void onStarted(@NonNull Run run, @NonNull TaskListener listener) {
        try (Scope scope = getTraceService().setupContext(run)) {
            this._onStarted(run, listener);
        }
    }

    public void _onStarted(@NonNull Run run, @NonNull TaskListener listener) {
    }

    @Override
    public final Environment setUpEnvironment(@NonNull AbstractBuild build, @NonNull Launcher launcher, @NonNull BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        try (Scope ignored = getTraceService().setupContext(build)) {
            return this._setUpEnvironment(build, launcher, listener);
        }
    }

    @NonNull
    public Environment _setUpEnvironment(@NonNull AbstractBuild build, @NonNull Launcher launcher, @NonNull BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        return new Environment() {
        };
    }

    @Override
    public final void onDeleted(@NonNull Run run) {
        // on delete event of a build that is running, there are remaining steps, skip verification
        try (Scope ignored = getTraceService().setupContext(run, false)) {
            this._onDeleted(run);
        }
    }

    public void _onDeleted(@NonNull Run run) {
    }

    @NonNull
    public OtelTraceService getTraceService() {
        return otelTraceService;
    }

    @NonNull
    public Tracer getTracer() {
        return tracer;
    }

}
