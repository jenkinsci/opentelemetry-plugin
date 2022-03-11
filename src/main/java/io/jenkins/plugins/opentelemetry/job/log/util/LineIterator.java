/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import io.jenkins.plugins.opentelemetry.job.RunFlowNodeIdentifier;
import org.kohsuke.stapler.Stapler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpSession;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    class SimpleLineBytesToLineNumberConverter implements LineBytesToLineNumberConverter {
        final ConcurrentMap<Long, Long> context = new ConcurrentHashMap<>();

        @Nullable
        @Override
        public Long getLogLineFromLogBytes(long bytes) {
            return context.get(bytes);
        }

        @Override
        public void putLogBytesToLogLine(long bytes, long line) {
            context.put(bytes, line);
        }
    }

    class JenkinsHttpSessionLineBytesToLineNumberConverter implements LineBytesToLineNumberConverter {
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
            HttpSession session = Stapler.getCurrentRequest().getSession();
            String sessionAttributeKey = getSessionAttributeKey();
            Map<Long, Long> context = (Map<Long, Long>) session.getAttribute(sessionAttributeKey);
            if (context == null) {
                return null;
            }
            return context.get(bytes);
        }

        @Override
        public void putLogBytesToLogLine(long bytes, long line) {
            HttpSession session = Stapler.getCurrentRequest().getSession();
            String sessionAttributeKey = getSessionAttributeKey();
            Map<Long, Long> context = (Map<Long, Long>) session.getAttribute(sessionAttributeKey);
            if (context == null) {
                context = new LinkedHashMap<>();
                session.setAttribute(sessionAttributeKey, context);
            }
            context.put(bytes, line);
        }

        @Nonnull
        private String getSessionAttributeKey() {
            return new RunFlowNodeIdentifier(jobFullName, runNumber, flowNodeId).getId();
        }
    }
}
