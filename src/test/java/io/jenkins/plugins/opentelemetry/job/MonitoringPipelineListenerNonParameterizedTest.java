/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

import hudson.model.Computer;
import io.jenkins.plugins.opentelemetry.OpenTelemetryAttributesAction;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.testing.trace.SpanMock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@WithJenkins
class MonitoringPipelineListenerNonParameterizedTest {

    private static JenkinsRule jenkinsRule;

    private static final StepContext stepContext = Mockito.mock(StepContext.class);
    private static final OtelTraceService otelTraceService = Mockito.mock(OtelTraceService.class);
    private static final WorkflowRun workflowRun = Mockito.mock(WorkflowRun.class);
    private static final String TEST_SPAN_NAME = "test-span";
    private static final String SH_STEP_SPAN_NAME = "sh-span";
    private final FlowNode flowNode = Mockito.mock(FlowNode.class);
    private final MonitoringPipelineListener monitoringPipelineListener = new MonitoringPipelineListener();
    private SpanMock testSpan;

    @BeforeAll
    static void commonSetup(JenkinsRule rule) throws Exception {
        jenkinsRule = rule;
        // Jenkins must have been initialized.
        assertNotNull(Jenkins.getInstanceOrNull());

        Mockito.when(stepContext.get(WorkflowRun.class)).thenReturn(workflowRun);
    }

    @BeforeEach
    void setup() throws Exception {
        monitoringPipelineListener.setOpenTelemetryTracerService(otelTraceService);

        testSpan = new SpanMock(TEST_SPAN_NAME);
        testSpan.setAttribute("caller.name", "testuser");

        Mockito.when(stepContext.get(FlowNode.class)).thenReturn(flowNode);

        Mockito.when(otelTraceService.getSpan(workflowRun)).thenReturn(testSpan);
        Mockito.when(otelTraceService.getSpan(workflowRun, flowNode)).thenReturn(testSpan);
    }

    @AfterEach
    void cleanup() {
        testSpan.end();
    }

    @Test
    void testSetAttributesToSpan() throws Exception {
        // Pass an empty list. All spans are allowed to get attributes.
        setupAttributesActionStubs(new ArrayList<>());

        try (MockedStatic<Span> mockedStatic = mockStatic(Span.class)) {
            // Span.current() should return the mocked span.
            mockedStatic.when(Span::current).thenReturn(testSpan);
            assertEquals(testSpan, Span.current());

            // The span should contain only 1 attribute.
            assertEquals(1, testSpan.getAttributes().size());
            assertTrue(testSpan.getAttributes().containsKey(AttributeKey.stringKey("caller.name")));
            assertEquals("testuser", testSpan.getAttributes().get(AttributeKey.stringKey("caller.name")));

            Step step = Mockito.mock(Step.class);
            monitoringPipelineListener.notifyOfNewStep(step, stepContext);

            // The span should now contain the computer and child action attributes as well.
            assertEquals(3, testSpan.getAttributes().size());
            assertTrue(testSpan.getAttributes().containsKey(AttributeKey.stringKey("caller.name")));
            assertEquals("testuser", testSpan.getAttributes().get(AttributeKey.stringKey("caller.name")));

            for (String component : Arrays.asList("computer", "child")) {
                AttributeKey<String> attributeKey =
                        AttributeKey.stringKey("attribute.from." + component + ".action.applied");
                assertTrue(testSpan.getAttributes().containsKey(attributeKey));
                assertEquals("true", testSpan.getAttributes().get(attributeKey));
            }
        }
    }

    @Test
    void testSetAttributesToSpanWithNotAllowedSpanId() throws Exception {
        String testSpanId = testSpan.getSpanContext().getSpanId();
        setupAttributesActionStubs(Collections.singletonList(testSpanId));

        SpanMock shSpan = new SpanMock(SH_STEP_SPAN_NAME);

        assertNotEquals(
                testSpan.getSpanContext().getSpanId(), shSpan.getSpanContext().getSpanId());

        try (MockedStatic<Span> mockedStatic = mockStatic(Span.class)) {
            // Span.current() should return the mocked span.
            mockedStatic.when(Span::current).thenReturn(shSpan);
            assertEquals(shSpan, Span.current());

            // The span doesn't have any attributes.
            assertEquals(0, shSpan.getAttributes().size());

            Step step = Mockito.mock(Step.class);
            monitoringPipelineListener.notifyOfNewStep(step, stepContext);

            // The span should now contain only the computer action attribute.
            // The computer action allowedSpanIdList is empty while
            // the child action allowedSpanIdList has an id.
            // The id on the list is different from the id of the current span.
            assertEquals(1, shSpan.getAttributes().size());

            AttributeKey<String> attributeKey = AttributeKey.stringKey("attribute.from.computer.action.applied");
            assertTrue(shSpan.getAttributes().containsKey(attributeKey));
            assertEquals("true", shSpan.getAttributes().get(attributeKey));
        }
    }

    private void setupAttributesActionStubs(List<String> allowedIds) throws Exception {
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
