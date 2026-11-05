/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class AgentExportPathWarmUpTest {

    @Test
    public void sendsTwoEmptyExportRequestsToTheLoopbackEndpoint() {
        assertEquals(2, AgentExportPathWarmUp.run());
    }
}
