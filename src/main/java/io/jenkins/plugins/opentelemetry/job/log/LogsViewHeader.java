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
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class LogsViewHeader {
    final private static String messageFirstToken = " View logs in ";
    final private String backendName;
    final private String backendUrl;
    final private String backendIconUrl;


    public LogsViewHeader(String backendName, String backendUrl, String backendIconUrl) {
        this.backendName = backendName;
        this.backendUrl = backendUrl;
        this.backendIconUrl = backendIconUrl;
    }

    public String getMessage() {
        return messageFirstToken + backendName;
    }

    public long writeHeader(Writer w, FlowExecutionOwner.Executable context, Charset charset) throws IOException {
        ConsoleAnnotator consoleAnnotator = new ConsoleAnnotator() {
            @Override
            public ConsoleAnnotator annotate(@Nonnull Object context, @Nonnull MarkupText text) {
                StaplerRequest currentRequest = Stapler.getCurrentRequest();
                String iconRootContextRelativeUrl;
                if (currentRequest == null) { // unit test
                    iconRootContextRelativeUrl = backendIconUrl;
                } else {
                    iconRootContextRelativeUrl = currentRequest.getContextPath() + backendIconUrl;
                }

                text.addMarkup(0, 0, "<img src='" + iconRootContextRelativeUrl + "' />", "");
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
