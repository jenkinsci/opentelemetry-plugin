/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import io.jenkins.plugins.opentelemetry.job.log.LogsViewHeader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LogsViewHeaderTest {

    @Test
    public void test() throws IOException {
        LogsViewHeader logsViewHeader = new LogsViewHeader(
                "My Logs Capable Observability Backend",
                "https://observability.example.com/traceId=123456789",
                "/plugin/opentelemetry/images/svgs/opentelemetry.svg");
        StringWriter actualStringWriter = new StringWriter();
        logsViewHeader.writeHeader(actualStringWriter, null, StandardCharsets.UTF_8);
        System.out.println(actualStringWriter);
        String expected =
                "<img src='/plugin/opentelemetry/images/svgs/opentelemetry.svg' /> View logs in <a href='https://observability.example.com/traceId=123456789' target='_blank'>My Logs Capable Observability Backend</a>"
                        + "\n";
        Assertions.assertEquals(expected, actualStringWriter.toString());
    }
}
