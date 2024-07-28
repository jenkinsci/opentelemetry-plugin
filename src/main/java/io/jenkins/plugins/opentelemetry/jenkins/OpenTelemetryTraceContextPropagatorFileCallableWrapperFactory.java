/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.jenkins;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.FilePath;
import hudson.remoting.DelegatingCallable;
import io.jenkins.plugins.opentelemetry.opentelemetry.GlobalOpenTelemetrySdk;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.jenkinsci.remoting.RoleChecker;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Propagates trace context to Jenkins build agents
 */
@Extension
public class OpenTelemetryTraceContextPropagatorFileCallableWrapperFactory extends FilePath.FileCallableWrapperFactory {
    static final Logger LOGGER = Logger.getLogger(OpenTelemetryTraceContextPropagatorFileCallableWrapperFactory.class.getName());

    Set<String> ignoredPackages = Set.of(
        "hudson",
        "org.jenkinsci.plugins.durabletask");

    @Override
    public <T> DelegatingCallable<T, IOException> wrap(DelegatingCallable<T, IOException> callable) {
        if (isIgnoredRemotingInvocation(callable)) {
            return callable;
        } else {
            return new OTelDelegatingCallable<>(callable);
        }
    }

    private <T> boolean isIgnoredRemotingInvocation(DelegatingCallable<T, IOException> callable) {
        return ignoredPackages.contains(callable.getClass().getPackageName());
    }

    static class OTelDelegatingCallable<V, T extends Throwable> implements DelegatingCallable<V, T> {
        private static final long serialVersionUID = 1L;
        final DelegatingCallable<V, T> callable;
        final Map<String, String> w3cTraceContext;

        public OTelDelegatingCallable(DelegatingCallable<V, T> callable) {
            this.callable = callable;
            w3cTraceContext = new HashMap<>();
            W3CTraceContextPropagator.getInstance().inject(Context.current(), w3cTraceContext, (carrier, key, value) -> {
                assert carrier != null;
                carrier.put(key, value);
            });
            LOGGER.log(Level.FINER, () -> "Wrap " + callable + " to propagate trace context " + w3cTraceContext);
        }

        @Override
        public ClassLoader getClassLoader() {
            return callable.getClassLoader();
        }

        @Override
        public V call() throws T {
            if (!GlobalOpenTelemetrySdk.isInitialized()) {
                LOGGER.log(Level.INFO, () -> "Call " + callable + " before OpenTelemetry SDK was initialized. " + w3cTraceContext);
                return callable.call();
            }
            Context callerContext = W3CTraceContextPropagator.getInstance().extract(Context.current(), w3cTraceContext, new TextMapGetter<>() {
                @Override
                public Iterable<String> keys(@Nonnull Map<String, String> carrier) {
                    return carrier.keySet();
                }

                @Nullable
                @Override
                public String get(@Nullable Map<String, String> carrier, @Nonnull String key) {
                    assert carrier != null;
                    return carrier.get(key);
                }
            });
            LOGGER.log(Level.FINER, () -> "Call " + callable + " with trace context " + w3cTraceContext);
            Span span;
            if (LOGGER.isLoggable(Level.INFO)) { // FIXME log level
                span = GlobalOpenTelemetry
                    .getTracer(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
                    .spanBuilder("Call")
                    .setParent(callerContext)
                    .setAttribute("jenkins.remoting.callable.class", callable.getClass().getName())
                    .setAttribute("jenkins.remoting.callable", callable.toString())
                    .startSpan();
            } else {
                span = Span.fromContext(callerContext);
            }

            try (Scope scope = span.makeCurrent()) {
                return callable.call();
            } catch (Throwable t) {
                span.recordException(t);
                throw t;
            } finally {
                span.end();
            }
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            callable.checkRoles(checker);
        }
    }
}