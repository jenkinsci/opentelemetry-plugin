/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.jenkins;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.FilePath;
import hudson.remoting.DelegatingCallable;
import io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.opentelemetry.GlobalOpenTelemetrySdk;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.remoting.RoleChecker;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Propagates trace context to Jenkins build agents and, if enabled, create a span on the jenkins agent side for the remoting call.
 */
@Extension
public class OpenTelemetryTraceContextPropagatorFileCallableWrapperFactory extends FilePath.FileCallableWrapperFactory implements OpenTelemetryLifecycleListener {
    static final Logger LOGGER = Logger.getLogger(OpenTelemetryTraceContextPropagatorFileCallableWrapperFactory.class.getName());

    final AtomicBoolean remotingTracingEnabled = new AtomicBoolean(false);
    final AtomicBoolean buildAgentsInstrumentationEnabled = new AtomicBoolean(false);

    @Override
    public <T> DelegatingCallable<T, IOException> wrap(DelegatingCallable<T, IOException> callable) {
        if (buildAgentsInstrumentationEnabled.get()) {
            return new OTelDelegatingCallable<>(callable, remotingTracingEnabled.get());
        } else {
            return callable;
        }
    }

    @Inject
    public void setJenkinsOpenTelemetryPluginConfiguration(JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration) {
        ConfigProperties configProperties = jenkinsOpenTelemetryPluginConfiguration.getConfigProperties();
        this.buildAgentsInstrumentationEnabled.set(configProperties.getBoolean(ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_AGENTS_ENABLED.asProperty(), false));
        this.remotingTracingEnabled.set(configProperties.getBoolean(ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_REMOTING_ENABLED.asProperty(), false));
    }

    @Override
    public void afterConfiguration(ConfigProperties configProperties) {
        this.buildAgentsInstrumentationEnabled.set(configProperties.getBoolean(ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_AGENTS_ENABLED.asProperty(), false));
        this.remotingTracingEnabled.set(configProperties.getBoolean(ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_REMOTING_ENABLED.asProperty(), false));
    }

    static class OTelDelegatingCallable<V, T extends Throwable> implements DelegatingCallable<V, T> {
        @Serial
        private static final long serialVersionUID = 1L;
        final DelegatingCallable<V, T> callable;
        final Map<String, String> w3cTraceContext;
        final boolean remotingTracingEnabled;

        public OTelDelegatingCallable(DelegatingCallable<V, T> callable, boolean remotingTracingEnabled) {
            this.callable = callable;
            this.w3cTraceContext = new HashMap<>();
            W3CTraceContextPropagator.getInstance().inject(Context.current(), w3cTraceContext, (carrier, key, value) -> {
                assert carrier != null;
                carrier.put(key, value);
            });
            this.remotingTracingEnabled = remotingTracingEnabled;
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
            if (remotingTracingEnabled) {
                String spanName;
                String callableToString = callable.toString();
                if ("hudson.FilePath$FileCallableWrapper".equals(callable.getClass().getName()) && StringUtils.contains(callableToString, "@")) {
                    spanName = StringUtils.substringBefore(callableToString, "@");
                } else {
                    spanName = "Call";
                }
                span = GlobalOpenTelemetry
                    .getTracer(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME)
                    .spanBuilder(spanName)
                    .setParent(callerContext)
                    .setSpanKind(SpanKind.SERVER)
                    .setAttribute("jenkins.remoting.callable", callableToString)
                    .setAttribute("jenkins.remoting.callable.class", callable.getClass().getName())
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

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            callable.checkRoles(checker);
        }
    }
}