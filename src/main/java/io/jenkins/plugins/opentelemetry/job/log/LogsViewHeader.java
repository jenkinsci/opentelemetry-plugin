/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationOutputStream;
import hudson.console.ConsoleAnnotator;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.output.CountingOutputStream;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Renders the banner shown above build logs, including backend icon and deep-link URL.
 */
public class LogsViewHeader {
    private static final String messageFirstToken = " View logs in ";
    private final String backendName;
    private final String backendUrl;
    private final String backendIconUrl;

    /**
     * Creates a logs-view header descriptor.
     *
     * @param backendName backend display name
     * @param backendUrl absolute URL to open backend logs view
     * @param backendIconUrl backend icon path
     */
    public LogsViewHeader(String backendName, String backendUrl, String backendIconUrl) {
        this.backendName = backendName;
        this.backendUrl = backendUrl;
        this.backendIconUrl = backendIconUrl;
    }

    /**
     * Returns the message displayed in the Jenkins log header.
     *
     * @return header message
     */
    public String getMessage() {
        return messageFirstToken + backendName;
    }

    /**
     * Writes an annotated logs header to the given writer.
     *
     * @param w writer receiving the header
     * @param context flow execution context used by the annotator
     * @param charset output character set
     * @return number of bytes written
     * @throws IOException if writing fails
     */
    public long writeHeader(Writer w, FlowExecutionOwner.Executable context, Charset charset) throws IOException {
        ConsoleAnnotator<Object> consoleAnnotator = new ConsoleAnnotator<>() {
            @Override
            public ConsoleAnnotator<Object> annotate(@NonNull Object context, @NonNull MarkupText text) {
                StaplerRequest2 currentRequest = Stapler.getCurrentRequest2();
                String iconRootContextRelativeUrl;
                if (currentRequest == null) { // unit test
                    iconRootContextRelativeUrl = backendIconUrl;
                } else {
                    iconRootContextRelativeUrl = currentRequest.getContextPath() + backendIconUrl;
                }

                text.addMarkup(0, 0, "<img src='" + iconRootContextRelativeUrl + "' />", "");
                text.addMarkup(
                        messageFirstToken.length(),
                        messageFirstToken.length() + backendName.length(),
                        "<a href='" + backendUrl + "' target='_blank'>",
                        "</a>");
                return this;
            }
        };
        ConsoleAnnotationOutputStream<FlowExecutionOwner.Executable> caw =
                new ConsoleAnnotationOutputStream<>(w, consoleAnnotator, context, charset);
        try (CountingOutputStream cos = new CountingOutputStream(caw)) {
            cos.write((getMessage() + "\n").getBytes(StandardCharsets.UTF_8));
            cos.flush();
            return cos.getByteCount();
        }
    }
}
