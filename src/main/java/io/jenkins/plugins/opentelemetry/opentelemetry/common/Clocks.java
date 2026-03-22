/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.common;

import io.opentelemetry.sdk.common.Clock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utils for {@link Clock}
 */
public class Clocks {
    private Clocks() {}

    /**
     * Returns a monotonic clock whose values are offset from the default clock.
     *
     * @param offsetInNanos offset added to each time reading
     * @return a monotonic offset clock
     */
    public static Clock monotonicOffsetClock(long offsetInNanos) {
        return new MonotonicClock(new OffsetClock(offsetInNanos, Clock.getDefault()));
    }

    /**
     * @param offsetInNanos the duration to add, in nanos
     * @param baseClock the base clock to offset
     * @return an offset clock using the provided base clock
     */
    public static Clock offsetClock(long offsetInNanos, Clock baseClock) {
        return new OffsetClock(offsetInNanos, baseClock);
    }

    /**
     * Wraps the given clock to guarantee strictly increasing values.
     *
     * @param delegate the clock to wrap
     * @return a monotonic clock wrapper
     */
    public static Clock monotonicClock(Clock delegate) {
        return new MonotonicClock(delegate);
    }

    /**
     * Returns a monotonic wrapper around the default OpenTelemetry clock.
     *
     * @return a monotonic clock
     */
    public static Clock monotonicClock() {
        return monotonicClock(Clock.getDefault());
    }

    private static class OffsetClock implements Clock {
        private final long offsetInNanos;
        private final Clock baseClock;

        /**
         * @param offsetInNanos the duration to add, in nanos
         * @param baseClock     the base clock to add the duration to, not null
         */
        private OffsetClock(long offsetInNanos, Clock baseClock) {
            this.offsetInNanos = offsetInNanos;
            this.baseClock = baseClock;
        }

        @Override
        public long now() {
            return baseClock.now() + offsetInNanos;
        }

        @Override
        public long nanoTime() {
            return baseClock.nanoTime() + offsetInNanos;
        }

        @Override
        public String toString() {
            return "OffsetClock{" + "offsetInNanos=" + offsetInNanos + '}';
        }
    }

    private static class MonotonicClock implements Clock {
        private final Clock delegate;
        private final AtomicReference<Long> lastNanoTime = new AtomicReference<>(0L);

        public MonotonicClock(Clock delegate) {
            this.delegate = delegate;
        }

        @Override
        public long now() {
            return lastNanoTime.updateAndGet(current -> Math.max(current + 1, delegate.now()));
        }

        @Override
        public long now(boolean highPrecision) {
            return lastNanoTime.updateAndGet(current -> Math.max(current + 1, delegate.now(highPrecision)));
        }

        @Override
        public long nanoTime() {
            return lastNanoTime.updateAndGet(current -> Math.max(current + 1, delegate.nanoTime()));
        }
    }
}
