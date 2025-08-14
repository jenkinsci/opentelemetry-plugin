/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jenkins.plugins.opentelemetry.job.log.LogsViewHeader;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LogsViewHeaderTest {

    @Test
    void test() throws Exception {
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
        assertEquals(expected, actualStringWriter.toString());
    }
}
