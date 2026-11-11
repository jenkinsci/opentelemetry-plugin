/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

public class AgentExportPathWarmUpTest {

    @Test
    public void exportsTwoLogRecordsToTheLoopbackEndpoint() {
        assertEquals(2, AgentExportPathWarmUp.run(), "two non-empty export requests");
    }

    @Test
    public void endpointOfAnIpv4Address() throws Exception {
        InetAddress loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        assertEquals(
                "http://127.0.0.1:4318/v1/logs", AgentExportPathWarmUp.endpoint(new InetSocketAddress(loopback, 4318)));
    }

    @Test
    public void endpointOfAnIpv6AddressIsBracketed() throws Exception {
        byte[] ipv6Loopback = new byte[16];
        ipv6Loopback[15] = 1;
        InetAddress loopback = InetAddress.getByAddress(ipv6Loopback);
        assertEquals(
                "http://[0:0:0:0:0:0:0:1]:4318/v1/logs",
                AgentExportPathWarmUp.endpoint(new InetSocketAddress(loopback, 4318)));
    }
}
