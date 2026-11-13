/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The lock step records a short pause even when the resource is free: only a pause of 50 ms or more is a wait.
 */
class LockBlockStepHandlerTest {

    @Test
    void testPauseShorterThanTheThresholdIsNotAWait() {
        assertFalse(LockBlockStepHandler.isWait(1_000, 1_000));
        assertFalse(LockBlockStepHandler.isWait(1_000, 1_010));
        assertFalse(LockBlockStepHandler.isWait(1_000, 1_049));
    }

    @Test
    void testPauseOfTheThresholdOrMoreIsAWait() {
        assertTrue(LockBlockStepHandler.isWait(1_000, 1_050));
        assertTrue(LockBlockStepHandler.isWait(1_000, 1_100));
        assertTrue(LockBlockStepHandler.isWait(1_000, 11_298));
    }

    @Test
    void testPauseThatEndsBeforeItStartsIsNotAWait() {
        assertFalse(LockBlockStepHandler.isWait(1_100, 1_000));
    }
}
