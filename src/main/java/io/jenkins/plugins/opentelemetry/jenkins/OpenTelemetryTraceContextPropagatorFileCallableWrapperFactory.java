/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.jenkins;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.FilePath;
import hudson.remoting.DelegatingCallable;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.opentelemetry.GlobalOpenTelemetrySdk;
import io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.io.IOException;
import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.remoting.RoleChecker;

/**
 * Propagates trace context to Jenkins build agents and, if enabled, create a span on the jenkins agent side for the remoting call.
 */
@Extension
public class OpenTelemetryTraceContextPropagatorFileCallableWrapperFactory extends FilePath.FileCallableWrapperFactory
        implements OpenTelemetryLifecycleListener {
    static final Logger LOGGER =
            Logger.getLogger(OpenTelemetryTraceContextPropagatorFileCallableWrapperFactory.class.getName());

    final AtomicBoolean remotingTracingEnabled = new AtomicBoolean(false);
    final AtomicBoolean buildAgentsInstrumentationEnabled = new AtomicBoolean(false);

    /**
     * Wraps a callable to propagate trace context to the Jenkins build agent.
     *
     * @param <T> callable return type
     * @param callable callable to wrap
     * @return wrapped callable with trace context propagation
     */
    @Override
    public <T> DelegatingCallable<T, IOException> wrap(DelegatingCallable<T, IOException> callable) {
        if (buildAgentsInstrumentationEnabled.get()) {
            return new OTelDelegatingCallable<>(callable, remotingTracingEnabled.get());
        } else {
            return callable;
        }
    }

    /**
     * Configures remoting instrumentation from global plugin configuration.
     *
     * @param jenkinsOpenTelemetryPluginConfiguration global plugin configuration
     */
    @Inject
    public void setJenkinsOpenTelemetryPluginConfiguration(
            JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration) {
        ConfigProperties configProperties = jenkinsOpenTelemetryPluginConfiguration.getConfigProperties();
        this.buildAgentsInstrumentationEnabled.set(configProperties.getBoolean(
                ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_AGENTS_ENABLED.asProperty(), false));
        this.remotingTracingEnabled.set(configProperties.getBoolean(
                ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_REMOTING_ENABLED.asProperty(), false));
    }

    /**
     * Reconfigures remoting instrumentation after SDK configuration changes.
     *
     * @param configProperties updated SDK configuration properties
     */
    @Override
    public void afterConfiguration(@NonNull ConfigProperties configProperties) {
        this.buildAgentsInstrumentationEnabled.set(configProperties.getBoolean(
                ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_AGENTS_ENABLED.asProperty(), false));
        this.remotingTracingEnabled.set(configProperties.getBoolean(
                ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_REMOTING_ENABLED.asProperty(), false));
    }

    static class OTelDelegatingCallable<V, T extends Throwable> implements DelegatingCallable<V, T> {
        @Serial
        private static final long serialVersionUID = 1L;

        final DelegatingCallable<V, T> callable;
        final Map<String, String> w3cTraceContext;
        final boolean remotingTracingEnabled;

        /**
         * Creates a delegating callable that propagates W3C trace context.
         *
         * @param callable wrapped callable
         * @param remotingTracingEnabled whether to create a span on the agent side
         */
        public OTelDelegatingCallable(DelegatingCallable<V, T> callable, boolean remotingTracingEnabled) {
            this.callable = callable;
            this.w3cTraceContext = new HashMap<>();
            W3CTraceContextPropagator.getInstance()
                    .inject(Context.current(), w3cTraceContext, (carrier, key, value) -> {
                        assert carrier != null;
                        carrier.put(key, value);
                    });
            this.remotingTracingEnabled = remotingTracingEnabled;
            LOGGER.log(Level.FINER, () -> "Wrap " + callable + " to propagate trace context " + w3cTraceContext);
        }

        /**
         * Returns class loader from the wrapped callable.
         *
         * @return wrapped callable class loader
         */
        @Override
        public ClassLoader getClassLoader() {
            return callable.getClassLoader();
        }

        /**
         * Executes the wrapped callable with trace context restored from the caller side.
         *
         * @return callable result
         * @throws T if the callable throws
         */
        @Override
        public V call() throws T {
            if (!GlobalOpenTelemetrySdk.isInitialized()) {
                LOGGER.log(
                        Level.INFO,
                        () -> "Call " + callable + " before OpenTelemetry SDK was initialized. " + w3cTraceContext);
                return callable.call();
            }
            Context callerContext = W3CTraceContextPropagator.getInstance()
                    .extract(Context.current(), w3cTraceContext, new TextMapGetter<>() {
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
            if (remotingTracingEnabled) {
                String spanName;
                String callableToString = callable.toString();
                if ("hudson.FilePath$FileCallableWrapper"
                                .equals(callable.getClass().getName())
                        && StringUtils.contains(callableToString, "@")) {
                    spanName = StringUtils.substringBefore(callableToString, "@");
                } else {
                    spanName = "Call";
                }
                span = GlobalOpenTelemetry.getTracer(ExtendedJenkinsAttributes.INSTRUMENTATION_NAME)
                        .spanBuilder(spanName)
                        .setParent(callerContext)
                        .setSpanKind(SpanKind.SERVER)
                        .setAttribute("jenkins.remoting.callable", callableToString)
                        .setAttribute(
                                "jenkins.remoting.callable.class",
                                callable.getClass().getName())
                        .startSpan();
            } else {
                span = Span.fromContext(callerContext);
            }

            try (Scope scope = span.makeCurrent()) {
                return callable.call();
            } catch (Throwable t) {
                span.setStatus(StatusCode.ERROR, t.getMessage());
                span.recordException(t);
                throw t;
            } finally {
                span.end();
            }
        }

        /**
         * Delegates role checking to the wrapped callable.
         *
         * @param checker role checker
         * @throws SecurityException if role check fails
         */
        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            callable.checkRoles(checker);
        }
    }
}
