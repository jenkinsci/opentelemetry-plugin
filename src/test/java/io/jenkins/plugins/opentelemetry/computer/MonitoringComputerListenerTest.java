/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.computer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import hudson.model.Computer;
import hudson.slaves.DumbSlave;
import io.jenkins.plugins.opentelemetry.BaseIntegrationTest;
import io.jenkins.plugins.opentelemetry.OpenTelemetryAttributesAction;
import io.opentelemetry.api.common.AttributeKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonitoringComputerListenerTest extends BaseIntegrationTest {

    @Test
    void reconnectKeepsOneAttributesAction() throws Exception {
        DumbSlave agent = jenkinsRule.createOnlineSlave();
        Computer computer = agent.toComputer();
        assertEquals(1, computer.getActions(OpenTelemetryAttributesAction.class).size());
        OpenTelemetryAttributesAction action = computer.getAction(OpenTelemetryAttributesAction.class);

        for (int i = 0; i < 3; i++) {
            Map<AttributeKey<?>, Object> snapshot = action.getAttributes();
            Map<AttributeKey<?>, Object> expected = new HashMap<>(snapshot);
            computer.disconnect(null).get();
            computer.connect(true).get();
            jenkinsRule.waitOnline(agent);

            assertSame(action, computer.getAction(OpenTelemetryAttributesAction.class));
            assertNotSame(snapshot, action.getAttributes());
            assertEquals(expected, snapshot);
            assertEquals(expected, action.getAttributes());
        }

        assertEquals(1, computer.getActions(OpenTelemetryAttributesAction.class).size());
    }
}
