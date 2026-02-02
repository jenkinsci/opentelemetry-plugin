/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import static org.mockito.Mockito.mockStatic;

import hudson.model.Computer;
import io.jenkins.plugins.opentelemetry.OpenTelemetryAttributesAction;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.testing.trace.SpanMock;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class MonitoringPipelineListenerNonParameterizedTest {

    @ClassRule
    public static final JenkinsRule jenkinsRule = new JenkinsRule();

    private static final StepContext stepContext = Mockito.mock(StepContext.class);
    private static final OtelTraceService otelTraceService = Mockito.mock(OtelTraceService.class);
    private static final WorkflowRun workflowRun = Mockito.mock(WorkflowRun.class);
    private static final String TEST_SPAN_NAME = "test-span";
    private static final String SH_STEP_SPAN_NAME = "sh-span";
    private final FlowNode flowNode = Mockito.mock(FlowNode.class);
    private final MonitoringPipelineListener monitoringPipelineListener = new MonitoringPipelineListener();
    private SpanMock testSpan;

    @BeforeClass
    public static void commonSetup() throws IOException, InterruptedException {
        // Jenkins must have been initialized.
        Assert.assertNotNull(Jenkins.getInstanceOrNull());

        Mockito.when(stepContext.get(WorkflowRun.class)).thenReturn(workflowRun);
    }

    @Before
    public void setup() throws IOException, InterruptedException {
        monitoringPipelineListener.setOpenTelemetryTracerService(otelTraceService);

        testSpan = new SpanMock(TEST_SPAN_NAME);
        testSpan.setAttribute("caller.name", "testuser");

        Mockito.when(stepContext.get(FlowNode.class)).thenReturn(flowNode);

        Mockito.when(otelTraceService.getSpan(workflowRun)).thenReturn(testSpan);
        Mockito.when(otelTraceService.getSpan(workflowRun, flowNode)).thenReturn(testSpan);
    }

    @After
    public void cleanup() {
        testSpan.end();
    }

    @Test
    public void testSetAttributesToSpan() throws IOException, InterruptedException {
        // Pass an empty list. All spans are allowed to get attributes.
        setupAttributesActionStubs(new ArrayList<>());

        try (MockedStatic<Span> mockedStatic = mockStatic(Span.class)) {
            // Span.current() should return the mocked span.
            mockedStatic.when(Span::current).thenReturn(testSpan);
            Assert.assertEquals(testSpan, Span.current());

            // The span should contain only 1 attribute.
            Assert.assertEquals(1, testSpan.getAttributes().keySet().size());
            Assert.assertTrue(testSpan.getAttributes().containsKey(AttributeKey.stringKey("caller.name")));
            Assert.assertEquals("testuser", testSpan.getAttributes().get(AttributeKey.stringKey("caller.name")));

            Step step = Mockito.mock(Step.class);
            monitoringPipelineListener.notifyOfNewStep(step, stepContext);

            // The span should now contain the computer and child action attributes as well.
            Assert.assertEquals(3, testSpan.getAttributes().keySet().size());
            Assert.assertTrue(testSpan.getAttributes().containsKey(AttributeKey.stringKey("caller.name")));
            Assert.assertEquals("testuser", testSpan.getAttributes().get(AttributeKey.stringKey("caller.name")));

            for (String component : Arrays.asList("computer", "child")) {
                AttributeKey<String> attributeKey =
                        AttributeKey.stringKey("attribute.from." + component + ".action.applied");
                Assert.assertTrue(testSpan.getAttributes().containsKey(attributeKey));
                Assert.assertEquals("true", testSpan.getAttributes().get(attributeKey));
            }
        }
    }

    @Test
    public void testSetAttributesToSpanWithNotAllowedSpanId() throws IOException, InterruptedException {
        String testSpanId = testSpan.getSpanContext().getSpanId();
        setupAttributesActionStubs(Collections.singletonList(testSpanId));

        SpanMock shSpan = new SpanMock(SH_STEP_SPAN_NAME);

        Assert.assertNotEquals(
                testSpan.getSpanContext().getSpanId(), shSpan.getSpanContext().getSpanId());

        try (MockedStatic<Span> mockedStatic = mockStatic(Span.class)) {
            // Span.current() should return the mocked span.
            mockedStatic.when(Span::current).thenReturn(shSpan);
            Assert.assertEquals(shSpan, Span.current());

            // The span doesn't have any attributes.
            Assert.assertEquals(0, shSpan.getAttributes().keySet().size());

            Step step = Mockito.mock(Step.class);
            monitoringPipelineListener.notifyOfNewStep(step, stepContext);

            // The span should now contain only the computer action attribute.
            // The computer action allowedSpanIdList is empty while
            // the child action allowedSpanIdList has an id.
            // The id on the list is different from the id of the current span.
            Assert.assertEquals(1, shSpan.getAttributes().keySet().size());

            AttributeKey<String> attributeKey = AttributeKey.stringKey("attribute.from.computer.action.applied");
            Assert.assertTrue(shSpan.getAttributes().containsKey(attributeKey));
            Assert.assertEquals("true", shSpan.getAttributes().get(attributeKey));
        }
    }

    private void setupAttributesActionStubs(List<String> allowedIds) throws IOException, InterruptedException {
        Computer computer = Mockito.mock(Computer.class);

        Mockito.when(stepContext.get(Computer.class)).thenReturn(computer);

        // Computer AttributesAction stub.
        OpenTelemetryAttributesAction otelComputerAttributesAction = new OpenTelemetryAttributesAction();
        otelComputerAttributesAction
                .getAttributes()
                .put(AttributeKey.stringKey("attribute.from.computer.action.applied"), "true");
        Mockito.when(computer.getAction(OpenTelemetryAttributesAction.class)).thenReturn(otelComputerAttributesAction);

        // Child AttributesAction stub.
        OpenTelemetryAttributesAction otelChildAttributesAction = new OpenTelemetryAttributesAction();

        for (String id : allowedIds) {
            otelChildAttributesAction.addSpanIdToInheritanceAllowedList(id);
        }

        otelChildAttributesAction
                .getAttributes()
                .put(AttributeKey.stringKey("attribute.from.child.action.applied"), "true");
        Mockito.when(stepContext.get(OpenTelemetryAttributesAction.class)).thenReturn(otelChildAttributesAction);
    }
}
