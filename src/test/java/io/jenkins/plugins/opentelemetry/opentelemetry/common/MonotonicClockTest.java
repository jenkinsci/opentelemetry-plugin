/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.common;

import static org.junit.Assert.*;

import io.opentelemetry.sdk.common.Clock;
import org.junit.Test;

public class MonotonicClockTest {

    @Test
    public void nowHighPrecision() {

        long previousTimestamp = 0;
        Clock clock = Clocks.monotonicClock();
        long singleIncrements = 0;
        for (int i = 0; i < 10_000; i++) {
            long timestamp = clock.now(true);
            if (previousTimestamp >= timestamp) {
                fail("Timestamps are not monotonic");
            } else if (previousTimestamp + 1 == timestamp) {
                singleIncrements++;
            }
            previousTimestamp = timestamp;
        }
        System.out.println("Single increments: " + singleIncrements);
    }
}
