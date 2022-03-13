/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import io.jenkins.plugins.opentelemetry.job.RunFlowNodeIdentifier;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class LineIteratorTest {

    @Test
    public void testJenkinsHttpSessionLineBytesToLineNumberConverter() {
        final Map<RunFlowNodeIdentifier, Map<Long, Long>> context = new HashMap<>();


        LineIterator.JenkinsHttpSessionLineBytesToLineNumberConverter converterJob1Run1 = new LineIterator.JenkinsHttpSessionLineBytesToLineNumberConverter("job-1", 1, null) {
            @Override
            Map<RunFlowNodeIdentifier, Map<Long, Long>> getContext() {
                return context;
            }
        };

        converterJob1Run1.putLogBytesToLogLine(100, 111);
        converterJob1Run1.putLogBytesToLogLine(1_000, 1_111);
        converterJob1Run1.putLogBytesToLogLine(10_000, 11_111);

        LineIterator.JenkinsHttpSessionLineBytesToLineNumberConverter converterJob1Run1FlowNode1 = new LineIterator.JenkinsHttpSessionLineBytesToLineNumberConverter("job-1", 1, "1") {
            @Override
            Map<RunFlowNodeIdentifier, Map<Long, Long>> getContext() {
                return context;
            }
        };
        converterJob1Run1FlowNode1.putLogBytesToLogLine(100, 7_111);
        converterJob1Run1FlowNode1.putLogBytesToLogLine(1_000, 71_111);
        converterJob1Run1FlowNode1.putLogBytesToLogLine(10_000, 711_111);

        LineIterator.JenkinsHttpSessionLineBytesToLineNumberConverter converterJob2Run2 = new LineIterator.JenkinsHttpSessionLineBytesToLineNumberConverter("job-2", 2, null) {
            @Override
            Map<RunFlowNodeIdentifier, Map<Long, Long>> getContext() {
                return context;
            }
        };

        converterJob2Run2.putLogBytesToLogLine(100, 222);
        converterJob2Run2.putLogBytesToLogLine(1_000, 2_222);
        converterJob2Run2.putLogBytesToLogLine(10_000, 22_222);

        LineIterator.JenkinsHttpSessionLineBytesToLineNumberConverter converterJob2Run2FlowNode2 = new LineIterator.JenkinsHttpSessionLineBytesToLineNumberConverter("job-2", 2, "2") {
            @Override
            Map<RunFlowNodeIdentifier, Map<Long, Long>> getContext() {
                return context;
            }
        };
        converterJob2Run2FlowNode2.putLogBytesToLogLine(100, 7_222);
        converterJob2Run2FlowNode2.putLogBytesToLogLine(1_000, 72_222);
        converterJob2Run2FlowNode2.putLogBytesToLogLine(10_000, 722_222);


        assertEquals(Long.valueOf(111), converterJob1Run1.getLogLineFromLogBytes(100));
        assertEquals(Long.valueOf(7_111), converterJob1Run1FlowNode1.getLogLineFromLogBytes(100));
        assertEquals(Long.valueOf(222), converterJob2Run2.getLogLineFromLogBytes(100));
        assertEquals(Long.valueOf(7_222), converterJob2Run2FlowNode2.getLogLineFromLogBytes(100));
        assertEquals(Long.valueOf(722_222), converterJob2Run2FlowNode2.getLogLineFromLogBytes(10_000));

    }

}