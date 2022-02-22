/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.MarkupText;
import hudson.console.ConsoleAnnotationOutputStream;
import hudson.console.ConsoleAnnotator;
import org.apache.commons.io.output.CountingOutputStream;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class LogsViewHeader {
    final String messageFirstToken = "View logs in ";
    final String backendName;
    final String backendUrl;

    public LogsViewHeader(String backendName, String backendUrl) {
        this.backendName = backendName;
        this.backendUrl = backendUrl;
    }

    public String getMessage() {
        return messageFirstToken + backendName;
    }

    public long writeHeader(Writer w, FlowExecutionOwner.Executable context, Charset charset) throws IOException {
        ConsoleAnnotator consoleAnnotator = new ConsoleAnnotator() {
            @Override
            public ConsoleAnnotator annotate(@Nonnull Object context, @Nonnull MarkupText text) {
                // TODO add backend logo
                text.addMarkup(messageFirstToken.length(), messageFirstToken.length() + backendName.length(), "<a href='" + backendUrl + "' target='_blank'>", "</a>");
                return this;
            }
        };
        ConsoleAnnotationOutputStream<FlowExecutionOwner.Executable> caw = new ConsoleAnnotationOutputStream<>(w, consoleAnnotator, context, charset);
        CountingOutputStream cos = new CountingOutputStream(caw);
        cos.write((getMessage() + "\n").getBytes(StandardCharsets.UTF_8));
        cos.flush();
        return cos.getByteCount();
    }
}
