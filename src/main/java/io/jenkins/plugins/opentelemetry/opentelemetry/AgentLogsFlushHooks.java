/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.remoting.Channel;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.JenkinsJVM;

/**
 * Flushes the log records queued in the batch processor of the Jenkins agent's OpenTelemetry SDK before the agent
 * goes away.
 * <p>
 * The agent SDK is configured without the SDK shutdown hook, and ephemeral agents (e.g. Kubernetes pods) are
 * terminated right after the build released them, so the records of the last step that are still queued in the
 * {@link io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor} would be lost. Two hooks are installed on the
 * agent JVM:
 * <ul>
 *     <li>a flush when the remoting channel to the controller is closed, which happens when the controller
 *     disconnects the agent before deleting it,</li>
 *     <li>a JVM shutdown hook, run when the agent process exits on its own or receives {@code SIGTERM}
 *     (Kubernetes pod deletion).</li>
 * </ul>
 * Each flush waits at most {@link #HOOK_FLUSH_TIMEOUT_MILLIS} and never throws.
 */
public final class AgentLogsFlushHooks {

    private static final Logger logger = Logger.getLogger(AgentLogsFlushHooks.class.getName());

    /**
     * Max time a flush hook waits for the export. Kubernetes gives the agent pod a 30 s termination grace period.
     */
    static final long HOOK_FLUSH_TIMEOUT_MILLIS =
            Long.getLong(AgentLogsFlushHooks.class.getName() + ".flushTimeoutMillis", 10_000);

    private static final AgentLogsFlushHooks INSTANCE = new AgentLogsFlushHooks(
            () -> GlobalOpenTelemetrySdk.forceFlushLogs(HOOK_FLUSH_TIMEOUT_MILLIS),
            hook -> Runtime.getRuntime().addShutdownHook(hook),
            AgentExportPathWarmUp::run);

    private final BooleanSupplier flush;
    private final Consumer<Thread> shutdownHookRegistrar;
    private final Runnable exportPathWarmUp;
    private final long warmUpWaitMillis;
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean();
    private final CountDownLatch warmedUp = new CountDownLatch(1);
    private final Set<Channel> channels = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    @VisibleForTesting
    AgentLogsFlushHooks(@NonNull BooleanSupplier flush, @NonNull Consumer<Thread> shutdownHookRegistrar) {
        this(flush, shutdownHookRegistrar, () -> {});
    }

    @VisibleForTesting
    AgentLogsFlushHooks(
            @NonNull BooleanSupplier flush,
            @NonNull Consumer<Thread> shutdownHookRegistrar,
            @NonNull Runnable exportPathWarmUp) {
        this(flush, shutdownHookRegistrar, exportPathWarmUp, HOOK_FLUSH_TIMEOUT_MILLIS);
    }

    @VisibleForTesting
    AgentLogsFlushHooks(
            @NonNull BooleanSupplier flush,
            @NonNull Consumer<Thread> shutdownHookRegistrar,
            @NonNull Runnable exportPathWarmUp,
            long warmUpWaitMillis) {
        this.flush = flush;
        this.shutdownHookRegistrar = shutdownHookRegistrar;
        this.exportPathWarmUp = exportPathWarmUp;
        this.warmUpWaitMillis = warmUpWaitMillis;
    }

    /**
     * Install the flush hooks on the current Jenkins agent JVM, once per JVM for the shutdown hook and once per
     * remoting channel for the channel close hook. No-op on the Jenkins controller.
     * Must be invoked from a callable executed on the agent so that {@link Channel#current()} is available.
     */
    public static void installOnAgent() {
        if (JenkinsJVM.isJenkinsJVM()) {
            return;
        }
        INSTANCE.install(Channel.current());
    }

    @VisibleForTesting
    void install(@CheckForNull Channel channel) {
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            try {
                Thread hook = new Thread(() -> flushQuietly("jvm-shutdown"), "OpenTelemetry agent logs flush");
                shutdownHookRegistrar.accept(hook);
                logger.log(Level.FINE, "Agent logs flush shutdown hook registered");
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Failure to register the agent logs flush shutdown hook", e);
            }
            // The agent loads the plugin classes lazily from the controller. Resolve them once while the channel is
            // open: a flush that first resolves them after the channel closed fails with NoClassDefFoundError.
            // A flush alone is not enough: the first install often runs before the SDK of the agent is configured (the
            // first step of a slow agent starts after the preOnline timeout), then the flush skips the SDK classes; and
            // it exports nothing when no record is queued, so it never resolves the classes of the HTTP export path
            try {
                try {
                    int flushPathClasses =
                            GlobalOpenTelemetrySdk.flushPathClasses().size();
                    logger.log(Level.FINE, () -> "Resolved " + flushPathClasses + " agent logs flush classes");
                    exportPathWarmUp.run();
                } catch (RuntimeException | LinkageError e) {
                    logger.log(Level.INFO, "Failure to warm up the agent log export path", e);
                }
                flushQuietly("warm-up");
            } finally {
                warmedUp.countDown();
            }
        } else {
            // the SDK configuration callable and the first steps can install the hooks concurrently: a step must not
            // start, and possibly end with the channel closing, before the warm-up of the other installer completed
            awaitWarmUp();
        }
        if (channel == null) {
            logger.log(Level.FINE, "No remoting channel, agent logs flush on channel close not registered");
            return;
        }
        if (channels.add(channel)) {
            try {
                AtomicBoolean flushed = new AtomicBoolean();
                channel.addListener(new Channel.Listener() {
                    @Override
                    public void onClosed(Channel closedChannel, IOException cause) {
                        // remoting may notify the listener more than once
                        if (flushed.compareAndSet(false, true)) {
                            flushQuietly("channel-closed " + closedChannel.getName());
                        }
                    }
                });
                logger.log(Level.FINE, () -> "Agent logs flush on close registered for channel " + channel.getName());
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Failure to register the agent logs flush on channel close", e);
            }
        }
    }

    private void awaitWarmUp() {
        try {
            if (!warmedUp.await(warmUpWaitMillis, TimeUnit.MILLISECONDS)) {
                logger.log(Level.FINE, () -> "Agent logs flush warm-up still running after " + warmUpWaitMillis + "ms");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Never throws. Note that java.util.logging resets its handlers in its own JVM shutdown hook, so failures of the
     * {@code jvm-shutdown} flush are usually not logged.
     */
    @VisibleForTesting
    void flushQuietly(String trigger) {
        try {
            if (!flush.getAsBoolean()) {
                logger.log(Level.FINE, () -> "Agent logs flush " + trigger + " did not complete successfully");
            }
        } catch (RuntimeException | LinkageError e) {
            logger.log(Level.WARNING, "Failure to flush the agent logs on " + trigger, e);
        }
    }
}
