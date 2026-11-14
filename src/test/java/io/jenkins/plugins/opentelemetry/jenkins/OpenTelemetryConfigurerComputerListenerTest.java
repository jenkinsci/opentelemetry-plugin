/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.jenkins;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Computer;
import hudson.remoting.Channel;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OpenTelemetryConfiguration;
import io.jenkins.plugins.opentelemetry.semconv.SemConvStability;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for <a href="https://github.com/jenkinsci/opentelemetry-plugin/issues/1285">issue #1285</a>:
 * {@link OpenTelemetryConfigurerComputerListener#preOnline(Computer, Channel, hudson.FilePath, hudson.model.TaskListener)}
 * used to block indefinitely on the agent configuration RPC, which left RTT-sensitive agents exposed to being
 * kicked offline if the underlying channel closed before the RPC completed.
 */
class OpenTelemetryConfigurerComputerListenerTest {

    @Test
    void preOnlineReturnsPromptlyWhenAgentConfigurationRpcNeverCompletes() throws Exception {
        OpenTelemetryConfigurerComputerListener listener = newListener(Duration.ofMillis(100));
        Computer computer = mockComputer("agent-high-rtt");
        Channel channel = mock(Channel.class);
        when(channel.callAsync(any())).thenReturn(new NeverCompletingFuture());

        long startNanos = System.nanoTime();
        assertDoesNotThrow(() -> listener.preOnline(computer, channel, null, null));
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertTrue(
                elapsedMillis < 5_000,
                "preOnline() must return once the configured timeout elapses instead of blocking indefinitely, took "
                        + elapsedMillis + "ms");
    }

    @Test
    void preOnlineDoesNotPropagateExecutionExceptionFromAgent() throws Exception {
        OpenTelemetryConfigurerComputerListener listener = newListener(Duration.ofSeconds(10));
        Computer computer = mockComputer("agent-failing-rpc");
        Channel channel = mock(Channel.class);
        CompletableFuture<Object> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("simulated remoting failure"));
        when(channel.callAsync(any())).thenReturn(new DelegatingFuture(failed));

        assertDoesNotThrow(() -> listener.preOnline(computer, channel, null, null));
    }

    private static Computer mockComputer(String name) {
        Computer computer = mock(Computer.class);
        when(computer.getName()).thenReturn(name);
        return computer;
    }

    private static OpenTelemetryConfigurerComputerListener newListener(Duration preOnlineTimeout) throws Exception {
        OpenTelemetryConfigurerComputerListener listener = new OpenTelemetryConfigurerComputerListener();
        listener.buildAgentsInstrumentationEnabled.set(true);

        OpenTelemetryConfiguration openTelemetryConfiguration = mock(OpenTelemetryConfiguration.class);
        when(openTelemetryConfiguration.toOpenTelemetryProperties()).thenReturn(Collections.emptyMap());
        when(openTelemetryConfiguration.toOpenTelemetryResourceAsMap()).thenReturn(Collections.emptyMap());

        JenkinsOpenTelemetryPluginConfiguration pluginConfiguration =
                mock(JenkinsOpenTelemetryPluginConfiguration.class);
        when(pluginConfiguration.getSemConvStability()).thenReturn(SemConvStability.OTEL);
        when(pluginConfiguration.toOpenTelemetryConfiguration()).thenReturn(openTelemetryConfiguration);
        listener.setJenkinsOpenTelemetryPluginConfiguration(pluginConfiguration);

        Field timeoutField = OpenTelemetryConfigurerComputerListener.class.getDeclaredField("preOnlineTimeout");
        timeoutField.setAccessible(true);
        timeoutField.set(listener, preOnlineTimeout);

        return listener;
    }

    /**
     * A {@link hudson.remoting.Future} that never completes, simulating a build agent configuration RPC sent over
     * a slow or RTT-bound remoting channel.
     */
    private static final class NeverCompletingFuture implements hudson.remoting.Future<Object> {
        private final CompletableFuture<Object> delegate = new CompletableFuture<>();

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            return delegate.get();
        }

        @Override
        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }
    }

    /**
     * Adapts a {@link CompletableFuture} to {@link hudson.remoting.Future}.
     */
    private static final class DelegatingFuture implements hudson.remoting.Future<Object> {
        private final CompletableFuture<Object> delegate;

        DelegatingFuture(CompletableFuture<Object> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            return delegate.get();
        }

        @Override
        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }
    }
}
