/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.jenkins.plugins.opentelemetry.job.RunFlowNodeIdentifier;
import io.jenkins.plugins.opentelemetry.job.log.LogLine;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public interface LogLineIterator <Id> extends Iterator<LogLine<Id>> {
    void skipLines(Id toLogLineId);

    interface LogLineBytesToLogLineIdMapper<Id> {
        /**
         * @return {@code null} if unknown
         */
        @Nullable
        Id getLogLineIdFromLogBytes(long bytes);

        void putLogBytesToLogLineId(long bytes, Id timestampInNanos);
    }

    /**
     * Converter gets garbage collected when the HTTP session expires
     */
    class JenkinsHttpSessionLineBytesToLogLineIdMapper<Id> implements LogLineBytesToLogLineIdMapper<Id> {
        private final static Logger logger = Logger.getLogger(JenkinsHttpSessionLineBytesToLogLineIdMapper.class.getName());

        public static final String HTTP_SESSION_KEY = "JenkinsHttpSessionLineBytesToLineNumberConverter";
        final String jobFullName;
        final int runNumber;
        @Nullable
        final String flowNodeId;

        public JenkinsHttpSessionLineBytesToLogLineIdMapper(String jobFullName, int runNumber, @Nullable String flowNodeId) {
            this.jobFullName = jobFullName;
            this.runNumber = runNumber;
            this.flowNodeId = flowNodeId;
        }

        @Nullable
        @Override
        public Id getLogLineIdFromLogBytes(long bytes) {
            RunFlowNodeIdentifier contextKey = new RunFlowNodeIdentifier(jobFullName, runNumber, flowNodeId);
            return Optional
                .ofNullable(getContext().get(contextKey))
                .map(d -> d.get(bytes))
                .orElse(null);

        }

        @Override
        public void putLogBytesToLogLineId(long bytes, Id logLineId) {
            RunFlowNodeIdentifier contextKey = new RunFlowNodeIdentifier(jobFullName, runNumber, flowNodeId);
            getContext().computeIfAbsent(contextKey, runFlowNodeIdentifier -> new HashMap<>()).put(bytes, logLineId);
        }

        Map<RunFlowNodeIdentifier, Map<Long, Id>> getContext() {
            StaplerRequest currentRequest = Stapler.getCurrentRequest();
            if (currentRequest == null) {
                // happens when reading logs is not tied to a web request
                // (e.g. API call from within a pipeline as described in https://github.com/jenkinsci/opentelemetry-plugin/issues/564)
                logger.log(Level.WARNING, "No current request found, default to default LogLineNumber context");
                return new HashMap<>();
            }
            HttpSession session = currentRequest.getSession();
            synchronized (session) {
                Map<RunFlowNodeIdentifier, Map<Long, Id>> context = (Map<RunFlowNodeIdentifier, Map<Long, Id>>) session.getAttribute(HTTP_SESSION_KEY);
                if (context == null) {
                    context = new HashMap<>();
                    session.setAttribute(HTTP_SESSION_KEY, context);
                }
                return context;
            }
        }
    }
}
