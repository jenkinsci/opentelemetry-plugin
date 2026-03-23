/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.jenkins.plugins.opentelemetry.job.RunFlowNodeIdentifier;
import io.jenkins.plugins.opentelemetry.job.log.LogLine;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/** Iterator of log lines that supports repositioning to a known log-line identifier. */
public interface LogLineIterator<Id> extends Iterator<LogLine<Id>> {
    /**
     * Advances the iterator so subsequent reads begin after the given log-line identifier.
     *
     * @param toLogLineId log-line identifier to skip to
     */
    void skipLines(Id toLogLineId);

    /** Mapper between byte offsets and backend-specific log-line identifiers. */
    interface LogLineBytesToLogLineIdMapper<Id> {
        /**
         * @return {@code null} if unknown
         */
        @Nullable
        Id getLogLineIdFromLogBytes(long bytes);

        /**
         * Stores the mapping between byte offset and corresponding log-line identifier.
         *
         * @param bytes byte offset in the streamed log
         * @param timestampInNanos backend-specific log-line identifier
         */
        void putLogBytesToLogLineId(long bytes, Id timestampInNanos);
    }

    /**
     * Converter gets garbage collected when the HTTP session expires
     */
    class JenkinsHttpSessionLineBytesToLogLineIdMapper<Id> implements LogLineBytesToLogLineIdMapper<Id> {
        private static final Logger logger =
                Logger.getLogger(JenkinsHttpSessionLineBytesToLogLineIdMapper.class.getName());

        /** Session attribute key used to store byte-offset mappings. */
        public static final String HTTP_SESSION_KEY = "JenkinsHttpSessionLineBytesToLineNumberConverter";
        final String jobFullName;
        final int runNumber;

        @Nullable
        final String flowNodeId;

        /**
         * Creates an HTTP-session-backed byte-offset mapper for a specific run and optional flow node.
         *
         * @param jobFullName Jenkins job full name
         * @param runNumber Jenkins run number
         * @param flowNodeId optional flow-node identifier
         */
        public JenkinsHttpSessionLineBytesToLogLineIdMapper(
                String jobFullName, int runNumber, @Nullable String flowNodeId) {
            this.jobFullName = jobFullName;
            this.runNumber = runNumber;
            this.flowNodeId = flowNodeId;
        }

        /**
         * Resolves the nearest known log-line identifier for the given byte offset.
         *
         * @param bytes byte offset in the streamed log
         * @return matching log-line identifier, or {@code null} when unknown
         */
        @Nullable
        @Override
        public Id getLogLineIdFromLogBytes(long bytes) {
            RunFlowNodeIdentifier contextKey = new RunFlowNodeIdentifier(jobFullName, runNumber, flowNodeId);
            return Optional.ofNullable(getContext().get(contextKey))
                    .map(d -> d.get(bytes))
                    .orElse(null);
        }

                /**
                 * Stores a byte offset to log-line identifier mapping for the current run context.
                 *
                 * @param bytes byte offset in the streamed log
                 * @param logLineId backend-specific log-line identifier
                 */
        @Override
        public void putLogBytesToLogLineId(long bytes, Id logLineId) {
            RunFlowNodeIdentifier contextKey = new RunFlowNodeIdentifier(jobFullName, runNumber, flowNodeId);
            getContext()
                    .computeIfAbsent(contextKey, runFlowNodeIdentifier -> new HashMap<>())
                    .put(bytes, logLineId);
        }

        Map<RunFlowNodeIdentifier, Map<Long, Id>> getContext() {
            StaplerRequest2 currentRequest = Stapler.getCurrentRequest2();
            if (currentRequest == null) {
                // happens when reading logs is not tied to a web request
                // (e.g. API call from within a pipeline as described in
                // https://github.com/jenkinsci/opentelemetry-plugin/issues/564)
                logger.log(Level.WARNING, "No current request found, default to default LogLineNumber context");
                return new HashMap<>();
            }
            HttpSession session = currentRequest.getSession();
            synchronized (session) {
                Map<RunFlowNodeIdentifier, Map<Long, Id>> context =
                        (Map<RunFlowNodeIdentifier, Map<Long, Id>>) session.getAttribute(HTTP_SESSION_KEY);
                if (context == null) {
                    context = new HashMap<>();
                    session.setAttribute(HTTP_SESSION_KEY, context);
                }
                return context;
            }
        }
    }
}
