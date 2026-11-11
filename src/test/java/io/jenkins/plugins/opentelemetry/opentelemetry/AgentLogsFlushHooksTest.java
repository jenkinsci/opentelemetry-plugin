/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.remoting.Channel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class AgentLogsFlushHooksTest {

    @Test
    public void shutdownHookIsRegisteredOnceAndFlushes() throws Exception {
        AtomicInteger flushes = new AtomicInteger();
        List<Thread> hooks = new ArrayList<>();
        AgentLogsFlushHooks hooksUnderTest = new AgentLogsFlushHooks(
                () -> {
                    flushes.incrementAndGet();
                    return true;
                },
                hooks::add);

        hooksUnderTest.install(null);
        hooksUnderTest.install(null);
        hooksUnderTest.install(null);

        assertEquals(1, hooks.size());
        assertEquals(1, flushes.get(), "warm-up flush at install time");
        Thread hook = hooks.get(0);
        hook.start();
        hook.join(5_000);
        assertEquals(2, flushes.get());
    }

    @Test
    public void exportPathWarmUpRunsOncePerJvmBeforeTheWarmUpFlush() {
        List<String> calls = new ArrayList<>();
        AgentLogsFlushHooks hooksUnderTest = new AgentLogsFlushHooks(
                () -> {
                    calls.add("flush");
                    return true;
                },
                hook -> {},
                () -> calls.add("export-path-warm-up"));

        hooksUnderTest.install(null);
        hooksUnderTest.install(mock(Channel.class));

        assertEquals(List.of("export-path-warm-up", "flush"), calls);
    }

    @Test
    public void concurrentInstallWaitsForTheWarmUpOfTheFirstInstall() throws Exception {
        CountDownLatch warmUpStarted = new CountDownLatch(1);
        CountDownLatch releaseWarmUp = new CountDownLatch(1);
        AgentLogsFlushHooks hooksUnderTest = new AgentLogsFlushHooks(() -> true, hook -> {}, () -> {
            warmUpStarted.countDown();
            try {
                releaseWarmUp.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread first = new Thread(() -> hooksUnderTest.install(null));
        first.start();
        assertTrue(warmUpStarted.await(5, TimeUnit.SECONDS));
        Thread second = new Thread(() -> hooksUnderTest.install(null));
        second.start();
        second.join(300);
        assertTrue(second.isAlive(), "the second install must wait for the warm-up");

        releaseWarmUp.countDown();
        second.join(5_000);
        first.join(5_000);
        assertFalse(second.isAlive());
        assertFalse(first.isAlive());
    }

    @Test
    public void concurrentInstallWaitIsBounded() throws Exception {
        CountDownLatch warmUpStarted = new CountDownLatch(1);
        CountDownLatch releaseWarmUp = new CountDownLatch(1);
        AgentLogsFlushHooks hooksUnderTest = new AgentLogsFlushHooks(
                () -> true,
                hook -> {},
                () -> {
                    warmUpStarted.countDown();
                    try {
                        releaseWarmUp.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                },
                100);

        Thread first = new Thread(() -> hooksUnderTest.install(null));
        first.start();
        try {
            assertTrue(warmUpStarted.await(5, TimeUnit.SECONDS));
            Thread second = new Thread(() -> hooksUnderTest.install(null));
            second.start();
            second.join(5_000);
            assertFalse(second.isAlive(), "the second install gives up waiting after the timeout");
        } finally {
            releaseWarmUp.countDown();
            first.join(5_000);
        }
    }

    @Test
    public void exportPathWarmUpFailureDoesNotPreventTheHooks() {
        List<Thread> hooks = new ArrayList<>();
        AgentLogsFlushHooks hooksUnderTest = new AgentLogsFlushHooks(() -> true, hooks::add, () -> {
            throw new NoClassDefFoundError("okhttp3/Call");
        });

        assertDoesNotThrow(() -> hooksUnderTest.install(null));
        assertEquals(1, hooks.size());
    }

    @Test
    public void channelCloseFlushesAndListenerIsRegisteredOncePerChannel() {
        AtomicInteger flushes = new AtomicInteger();
        AgentLogsFlushHooks hooksUnderTest = new AgentLogsFlushHooks(
                () -> {
                    flushes.incrementAndGet();
                    return true;
                },
                hook -> {});
        Channel channel = mock(Channel.class);
        when(channel.getName()).thenReturn("test-channel");
        Channel otherChannel = mock(Channel.class);
        when(otherChannel.getName()).thenReturn("other-channel");

        hooksUnderTest.install(channel);
        hooksUnderTest.install(channel);
        hooksUnderTest.install(otherChannel);

        ArgumentCaptor<Channel.Listener> listener = ArgumentCaptor.forClass(Channel.Listener.class);
        verify(channel, times(1)).addListener(listener.capture());
        verify(otherChannel, times(1)).addListener(any(Channel.Listener.class));
        assertEquals(1, flushes.get(), "only the warm-up flush, once per JVM");

        listener.getValue().onClosed(channel, new IOException("closed"));
        assertEquals(2, flushes.get());

        listener.getValue().onClosed(channel, new IOException("closed again"));
        assertEquals(2, flushes.get(), "flush only once per channel");
    }

    @Test
    public void failuresNeverPropagate() {
        AgentLogsFlushHooks hooksUnderTest = new AgentLogsFlushHooks(
                () -> {
                    throw new IllegalStateException("flush failure");
                },
                hook -> {
                    throw new IllegalStateException("Shutdown in progress");
                });
        Channel channel = mock(Channel.class);
        when(channel.getName()).thenReturn("test-channel");
        org.mockito.Mockito.doThrow(new IllegalStateException("closed"))
                .when(channel)
                .addListener(any(Channel.Listener.class));

        assertDoesNotThrow(() -> hooksUnderTest.install(channel));
        assertDoesNotThrow(() -> hooksUnderTest.flushQuietly("test"));
    }
}
