/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.common;

import io.opentelemetry.sdk.common.Clock;

public final class OffsetClock implements Clock {
    final long offsetInNanos;
    final Clock baseClock;

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
        return "OffsetClock{" +
            "offsetInNanos=" + offsetInNanos +
            '}';
    }

    /**
     *
     * @param offsetInNanos the duration to add, in nanos
     */
    public static Clock offsetClock(long offsetInNanos) {
        return new OffsetClock(offsetInNanos, Clock.getDefault());
    }
}
