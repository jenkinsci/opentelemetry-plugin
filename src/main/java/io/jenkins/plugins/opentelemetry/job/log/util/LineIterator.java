/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpSession;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.jenkins.plugins.opentelemetry.job.RunFlowNodeIdentifier;

public interface LineIterator extends Iterator<String> {
    void skipLines(long skip);

    interface LineBytesToLineNumberConverter {
        /**
         * @return {@code null} if unknown
         */
        @Nullable
        Long getLogLineFromLogBytes(long bytes);

        void putLogBytesToLogLine(long bytes, long line);

    }

    /**
     * Converter gets garbage collected when the HTTP session expires
     */
    class JenkinsHttpSessionLineBytesToLineNumberConverter implements LineBytesToLineNumberConverter {
        private final static Logger logger = Logger.getLogger(JenkinsHttpSessionLineBytesToLineNumberConverter.class.getName());

        public static final String HTTP_SESSION_KEY = "JenkinsHttpSessionLineBytesToLineNumberConverter";
        final String jobFullName;
        final int runNumber;
        final String flowNodeId;

        public JenkinsHttpSessionLineBytesToLineNumberConverter(String jobFullName, int runNumber, String flowNodeId) {
            this.jobFullName = jobFullName;
            this.runNumber = runNumber;
            this.flowNodeId = flowNodeId;
        }

        @Nullable
        @Override
        public Long getLogLineFromLogBytes(long bytes) {
            RunFlowNodeIdentifier contextKey = new RunFlowNodeIdentifier(jobFullName, runNumber, flowNodeId);
            return Optional
                .ofNullable(getContext().get(contextKey))
                .map(d -> d.get(bytes))
                .orElse(null);

        }

        @Override
        public void putLogBytesToLogLine(long bytes, long line) {
            RunFlowNodeIdentifier contextKey = new RunFlowNodeIdentifier(jobFullName, runNumber, flowNodeId);
            getContext().computeIfAbsent(contextKey, runFlowNodeIdentifier -> new HashMap<>()).put(bytes, line);
        }

        Map<RunFlowNodeIdentifier, Map<Long, Long>> getContext() {
            StaplerRequest currentRequest = Stapler.getCurrentRequest();
            if (currentRequest == null) {
                // happens when reading logs is not tied to a web request
                // (e.g. API call from within a pipeline as described in https://github.com/jenkinsci/opentelemetry-plugin/issues/564)
                logger.log(Level.WARNING, "No current request found, default to default LogLineNumber context");
                return new HashMap<>();
            }
            HttpSession session = currentRequest.getSession();
            synchronized (session) {
                Map<RunFlowNodeIdentifier, Map<Long, Long>> context = (Map<RunFlowNodeIdentifier, Map<Long, Long>>) session.getAttribute(HTTP_SESSION_KEY);
                if (context == null) {
                    context = new HashMap<>();
                    session.setAttribute(HTTP_SESSION_KEY, context);
                }
                return context;
            }
        }
    }
}
