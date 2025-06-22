/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.opentelemetry;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.io.IOException;
import javax.inject.Inject;

/**
 * {@link RunListener} that setups the OpenTelemetry {@link io.opentelemetry.context.Context}
 * with the current {@link Span}.
 */
public abstract class OtelContextAwareAbstractRunListener extends RunListener<Run<?, ?>> {

    private OtelTraceService otelTraceService;
    private Tracer tracer;
    private Meter meter;
    private ConfigProperties configProperties;

    @Inject
    public final void setOpenTelemetryTracerService(@NonNull OtelTraceService otelTraceService) {
        this.otelTraceService = otelTraceService;
    }

    @Inject
    public final void setJenkinsControllerOpenTelemetry(
            @NonNull JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry) {
        this.tracer = jenkinsControllerOpenTelemetry.getDefaultTracer();
        this.meter = jenkinsControllerOpenTelemetry.getDefaultMeter();
    }

    @Inject
    public final void setOpenTelemetry(@NonNull ReconfigurableOpenTelemetry jenkinsControllerOpenTelemetry) {
        this.configProperties = jenkinsControllerOpenTelemetry.getConfig();
    }

    @Override
    public final void onCompleted(@NonNull Run<?, ?> run, @NonNull TaskListener listener) {
        Span span = getTraceService().getSpan(run);
        try (Scope scope = span.makeCurrent()) {
            this._onCompleted(run, listener);
        }
    }

    public void _onCompleted(@NonNull Run<?, ?> run, @NonNull TaskListener listener) {}

    @Override
    public final void onFinalized(@NonNull Run<?, ?> run) {
        Span span = getTraceService().getSpan(run);
        try (Scope scope = span.makeCurrent()) {
            this._onFinalized(run);
        }
    }

    public void _onFinalized(Run<?, ?> run) {}

    @Override
    public final void onInitialize(@NonNull Run<?, ?> run) {
        this._onInitialize(run);
    }

    public void _onInitialize(@NonNull Run<?, ?> run) {}

    @Override
    public final void onStarted(@NonNull Run<?, ?> run, @NonNull TaskListener listener) {
        Span span = getTraceService().getSpan(run);
        try (Scope scope = span.makeCurrent()) {
            this._onStarted(run, listener);
        }
    }

    public void _onStarted(@NonNull Run<?, ?> run, @NonNull TaskListener listener) {}

    @Override
    public final Environment setUpEnvironment(
            @NonNull AbstractBuild build, @NonNull Launcher launcher, @NonNull BuildListener listener)
            throws IOException, InterruptedException, Run.RunnerAbortedException {
        Span span = getTraceService().getSpan(build);
        try (Scope ignored = span.makeCurrent()) {
            return this._setUpEnvironment(build, launcher, listener);
        }
    }

    @NonNull
    public Environment _setUpEnvironment(
            @NonNull AbstractBuild build, @NonNull Launcher launcher, @NonNull BuildListener listener)
            throws IOException, InterruptedException, Run.RunnerAbortedException {
        return new Environment() {};
    }

    @Override
    public final void onDeleted(@NonNull Run<?, ?> run) {
        Span span = getTraceService().getSpan(run);
        try (Scope ignored = span.makeCurrent()) {
            this._onDeleted(run);
        }
    }

    public void _onDeleted(@NonNull Run<?, ?> run) {}

    @NonNull
    public OtelTraceService getTraceService() {
        return otelTraceService;
    }

    @NonNull
    public Tracer getTracer() {
        return tracer;
    }

    @NonNull
    public Meter getMeter() {
        return meter;
    }

    protected ConfigProperties getConfigProperties() {
        return configProperties;
    }
}
