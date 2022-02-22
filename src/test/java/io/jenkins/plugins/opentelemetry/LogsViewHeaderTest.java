/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import io.jenkins.plugins.opentelemetry.job.log.LogsViewHeader;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class LogsViewHeaderTest {

    @Test
    public void test() throws IOException {
        LogsViewHeader logsViewHeader = new LogsViewHeader(
            "My Logs Capable Observability Backend",
            "https://observability.example.com/traceId=123456789",
            "/plugin/opentelemetry/images/24x24/opentelemetry.png");
        StringWriter actualStringWriter = new StringWriter();
        logsViewHeader.writeHeader(actualStringWriter, null,StandardCharsets.UTF_8);
        System.out.println(actualStringWriter);
        String expected = "<img src='/plugin/opentelemetry/images/24x24/opentelemetry.png' /> View logs in <a href='https://observability.example.com/traceId=123456789' target='_blank'>My Logs Capable Observability Backend</a>" + "\n";
        Assert.assertEquals(expected, actualStringWriter.toString());
    }
}
