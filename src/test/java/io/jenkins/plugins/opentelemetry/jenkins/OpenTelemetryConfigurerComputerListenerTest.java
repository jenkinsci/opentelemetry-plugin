/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.jenkins;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.model.Computer;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OpenTelemetryConfiguration;
import io.jenkins.plugins.opentelemetry.jenkins.OpenTelemetryConfigurerComputerListener.OpenTelemetryConfigurerMasterToSlaveCallable;
import io.jenkins.plugins.opentelemetry.opentelemetry.AgentExportClassesPreloader;
import io.jenkins.plugins.opentelemetry.semconv.SemConvStability;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Regression tests for <a href="https://github.com/jenkinsci/opentelemetry-plugin/issues/1285">issue #1285</a>:
 * {@link OpenTelemetryConfigurerComputerListener#preOnline(Computer, Channel, hudson.FilePath, hudson.model.TaskListener)}
 * used to block indefinitely on the agent configuration RPC, which left RTT-sensitive agents exposed to being
 * kicked offline if the underlying channel closed before the RPC completed.
 * <p>
 * With the export jars preloaded (the default), the configuration RPC goes through {@link Channel#call(Callable)},
 * otherwise through {@link Channel#callAsync(Callable)}: the tests stub both.
 */
class OpenTelemetryConfigurerComputerListenerTest {

    @Test
    void theConfigurationRpcGoesThroughCallWhenTheJarsArePreloaded() {
        assertTrue(AgentExportClassesPreloader.ENABLED, "the tests below exercise the preload path");
    }

    @Test
    void preOnlineReturnsPromptlyWhenAgentConfigurationRpcNeverCompletes() throws Exception {
        OpenTelemetryConfigurerComputerListener listener = newListener(Duration.ofMillis(100));
        Computer computer = mockComputer("agent-high-rtt");
        Channel channel = mock(Channel.class);
        CountDownLatch release = new CountDownLatch(1);
        when(channel.callAsync(any())).thenReturn(new NeverCompletingFuture());
        when(channel.call(any())).thenAnswer(invocation -> {
            release.await(10, TimeUnit.SECONDS);
            return null;
        });

        try {
            long startNanos = System.nanoTime();
            assertDoesNotThrow(() -> listener.preOnline(computer, channel, null, null));
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

            assertTrue(
                    elapsedMillis < 5_000,
                    "preOnline() must return once the configured timeout elapses instead of blocking indefinitely, took "
                            + elapsedMillis + "ms");
            verify(channel, timeout(5_000)).call(any());
        } finally {
            release.countDown();
        }
    }

    @Test
    void preOnlineDoesNotPropagateExecutionExceptionFromAgent() throws Exception {
        OpenTelemetryConfigurerComputerListener listener = newListener(Duration.ofSeconds(10));
        Computer computer = mockComputer("agent-failing-rpc");
        Channel channel = mock(Channel.class);
        CompletableFuture<Object> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("simulated remoting failure"));
        when(channel.callAsync(any())).thenReturn(new DelegatingFuture(failed));
        when(channel.call(any())).thenThrow(new IOException("simulated remoting failure"));

        assertDoesNotThrow(() -> listener.preOnline(computer, channel, null, null));
        verify(channel).call(any());
    }

    @Test
    void preOnlineTimeoutDoesNotInterruptTheAgentConfiguration() throws Exception {
        OpenTelemetryConfigurerComputerListener listener = newListener(Duration.ofMillis(100));
        Computer computer = mockComputer("agent-slow-configuration");
        Channel channel = mock(Channel.class);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean interrupted = new AtomicBoolean();
        when(channel.callAsync(any())).thenReturn(new NeverCompletingFuture());
        when(channel.call(any())).thenAnswer(invocation -> {
            try {
                completed.set(release.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                interrupted.set(true);
            } finally {
                done.countDown();
            }
            return null;
        });

        listener.preOnline(computer, channel, null, null);
        // preOnline() timed out, the configuration must go on in the background
        release.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "the configuration RPC was sent");
        assertFalse(interrupted.get(), "the configuration must not be interrupted on timeout");
        assertTrue(completed.get(), "the configuration completes after the timeout");
    }

    @Test
    void eachConfigurationHasAHigherRevision() throws Exception {
        OpenTelemetryConfigurerComputerListener listener = newListener(Duration.ofSeconds(10));
        Computer computer = mockComputer("agent");
        Channel channel = mock(Channel.class);
        when(channel.call(any())).thenReturn(null);

        listener.preOnline(computer, channel, null, null);
        listener.preOnline(computer, channel, null, null);

        ArgumentCaptor<OpenTelemetryConfigurerMasterToSlaveCallable> callables =
                ArgumentCaptor.forClass(OpenTelemetryConfigurerMasterToSlaveCallable.class);
        verify(channel, timeout(5_000).times(2)).call(callables.capture());
        List<OpenTelemetryConfigurerMasterToSlaveCallable> sent = callables.getAllValues();
        assertTrue(
                sent.get(1).revision > sent.get(0).revision,
                "revisions " + sent.get(0).revision + ", " + sent.get(1).revision);
    }

    @Test
    void agentAppliesOnlyANewerRevision() {
        Channel channel = channelWithProperties();
        AtomicInteger configurations = new AtomicInteger();

        assertTrue(callable(2).applyIfNewer(channel, configurations::incrementAndGet));
        assertFalse(callable(1).applyIfNewer(channel, configurations::incrementAndGet), "older revision skipped");
        assertFalse(callable(2).applyIfNewer(channel, configurations::incrementAndGet), "same revision skipped");
        assertTrue(callable(3).applyIfNewer(channel, configurations::incrementAndGet));
        assertEquals(2, configurations.get());
    }

    @Test
    void agentAppliesAgainOnANewChannel() {
        AtomicInteger configurations = new AtomicInteger();

        assertTrue(callable(5).applyIfNewer(channelWithProperties(), configurations::incrementAndGet));
        // e.g. the controller restarted: its revisions start again, on a new channel
        assertTrue(callable(1).applyIfNewer(channelWithProperties(), configurations::incrementAndGet));
        assertEquals(2, configurations.get());
    }

    @Test
    void agentWithoutChannelAlwaysApplies() {
        AtomicInteger configurations = new AtomicInteger();

        assertTrue(callable(2).applyIfNewer(null, configurations::incrementAndGet));
        assertTrue(callable(1).applyIfNewer(null, configurations::incrementAndGet));
        assertEquals(2, configurations.get());
    }

    @Test
    void failedConfigurationIsNotRecordedAsApplied() {
        Channel channel = channelWithProperties();

        assertThrows(
                IllegalStateException.class,
                () -> callable(2).applyIfNewer(channel, () -> {
                    throw new IllegalStateException("configuration failure");
                }));
        assertTrue(callable(1).applyIfNewer(channel, () -> {}), "nothing was applied, an older revision may apply");
    }

    private static OpenTelemetryConfigurerMasterToSlaveCallable callable(long revision) {
        return new OpenTelemetryConfigurerMasterToSlaveCallable(Map.of(), Map.of(), revision);
    }

    /**
     * A mocked channel whose {@link Channel#getProperty(Object)} and {@link Channel#setProperty(Object, Object)}
     * use a map, like a real channel.
     */
    private static Channel channelWithProperties() {
        Channel channel = mock(Channel.class);
        Map<Object, Object> properties = new ConcurrentHashMap<>();
        when(channel.getProperty((Object) any())).thenAnswer(invocation -> properties.get(invocation.getArgument(0)));
        when(channel.setProperty((Object) any(), any()))
                .thenAnswer(invocation -> properties.put(invocation.getArgument(0), invocation.getArgument(1)));
        return channel;
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
