/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.ExtensionList;
import hudson.model.Computer;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.OpenTelemetryAttributesAction;
import io.jenkins.plugins.opentelemetry.job.step.SpanAttribute;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ImplicitContextKeyed;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.trace.SpanBuilderMock;
import io.opentelemetry.sdk.testing.trace.SpanMock;
import io.opentelemetry.sdk.testing.trace.TracerMock;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Verify.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MonitoringPipelineListener}.
 * Using subclasses so that the non-parameterized tests
 * don't have to be run once for every parameter.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    MonitoringPipelineListenerTest.ParamTests.class,
    MonitoringPipelineListenerTest.NonParamTests.class
})
public class MonitoringPipelineListenerTest {

    @ClassRule
    public static final JenkinsRule jenkinsRule = new JenkinsRule();

    private static final StepContext stepContext = Mockito.mock(StepContext.class);
    private static final OtelTraceService otelTraceService = Mockito.mock(OtelTraceService.class);

    private static MonitoringPipelineListener monitoringPipelineListener;
    private static WorkflowRun workflowRun;

    @BeforeClass
    public static void commonSetup() throws IOException, InterruptedException {
        // Jenkins must have been initialized.
        Assert.assertNotNull(Jenkins.getInstanceOrNull());

        workflowRun = Mockito.mock(WorkflowRun.class);

        Mockito.when(stepContext.get(WorkflowRun.class)).thenReturn(workflowRun);
    }

    @RunWith(Parameterized.class)
    public static class ParamTests {

        private static final String START_NODE_ROOT_SPAN_NAME = "root-span";
        private static final String WITH_NEW_SPAN_NAME = "with-new-span";
        SpanBuilderMock spanBuilderMock = Mockito.spy(new SpanBuilderMock(WITH_NEW_SPAN_NAME));

        @Parameterized.Parameter(0)
        public String attributeKeyName;

        @Parameterized.Parameter(1)
        public Object attributeKeyObj;

        @Parameterized.Parameter(2)
        public Object attributeValueObj;

        @Parameterized.Parameters
        public static Collection<Object[]> argumentsActionScenarios() {
            return Arrays.asList(new Object[][] {
                { "with.new.span.boolean", AttributeKey.booleanKey("with.new.span.boolean"), true },
                { "with.new.span.string", AttributeKey.stringKey("with.new.span.string"), "true" },
                { "with.new.span.long", AttributeKey.longKey("with.new.span.long"), 2L },
                { "with.new.span.double", AttributeKey.doubleKey("with.new.span.double"), 2.22 },
            });
        }

        @Before
        public void setup() {
            ExtensionList<JenkinsControllerOpenTelemetry> jenkinsOpenTelemetries = jenkinsRule.getInstance().getExtensionList(JenkinsControllerOpenTelemetry.class);
            verify(jenkinsOpenTelemetries.size() == 1, "Number of jenkinsControllerOpenTelemetrys: %s", jenkinsOpenTelemetries.size());
            JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry = Mockito.spy(jenkinsOpenTelemetries.get(0));

            Tracer tracer = Mockito.spy(new TracerMock());

            Mockito.when(tracer.spanBuilder(WITH_NEW_SPAN_NAME)).thenReturn(spanBuilderMock);

            Mockito.when(jenkinsControllerOpenTelemetry.getDefaultTracer()).thenReturn(tracer);

            monitoringPipelineListener = new MonitoringPipelineListener();
            monitoringPipelineListener.jenkinsControllerOpenTelemetry = jenkinsControllerOpenTelemetry;

            Assert.assertNull(monitoringPipelineListener.getTracer());
            // postConstruct() calls the getDefaultTracer() method which needs to be stubbed in advance before using the tracer.
            // Manually invoke the postConstruct() method to re-apply the @PostConstruct logic.
            monitoringPipelineListener.postConstruct();

            Assert.assertNotNull(monitoringPipelineListener.getTracer());

            monitoringPipelineListener.setOpenTelemetryTracerService(otelTraceService);
        }

        @Test
        public void testOnStartWithNewSpanStep() {

            StepStartNode stepStartNode = Mockito.mock(StepStartNode.class);
            ArgumentsAction action = new ArgumentsAction() {
                @NotNull
                @Override
                protected Map<String, Object> getArgumentsInternal() {
                    Map<String, Object> map = new HashMap<>();
                    map.put("label", WITH_NEW_SPAN_NAME);
                    List<SpanAttribute> spanAttributes = new ArrayList<>();
                    SpanAttribute spanAttribute = new SpanAttribute(attributeKeyName, attributeValueObj, null, null);
                    spanAttributes.add(spanAttribute);
                    map.put("attributes", spanAttributes);
                    return map;
                }
            };
            Mockito.when(stepStartNode.getPersistentAction(ArgumentsAction.class)).thenReturn(action);

            Scope scope = Mockito.mock(Scope.class);

            SpanMock rootSpan = Mockito.spy(new SpanMock(START_NODE_ROOT_SPAN_NAME));

            Mockito.when(rootSpan.makeCurrent()).thenReturn(scope);

            Mockito.when(otelTraceService.getSpan(workflowRun)).thenReturn(rootSpan);
            Mockito.when(otelTraceService.getSpan(workflowRun, stepStartNode)).thenReturn(rootSpan);

            monitoringPipelineListener.setOpenTelemetryTracerService(otelTraceService);
            Assert.assertNotNull(monitoringPipelineListener.getTracerService().getSpan(workflowRun));

            SpanMock newSpan = Mockito.spy(new SpanMock(WITH_NEW_SPAN_NAME));
            Mockito.when(monitoringPipelineListener.getTracer().spanBuilder(WITH_NEW_SPAN_NAME).startSpan()).thenReturn(newSpan);

            try (MockedStatic<Span> mockedStaticSpan = mockStatic(Span.class);
                 MockedStatic<Context> mockedStaticContext = mockStatic(Context.class)) {
                // Span.current() should return the mocked span.
                mockedStaticSpan.when(Span::current).thenReturn(rootSpan);
                Assert.assertEquals(rootSpan, Span.current());

                mockedStaticSpan.when(() -> Span.fromContext(any())).thenReturn(rootSpan);

                Context context = Mockito.mock(Context.class);
                when(context.with(any(ImplicitContextKeyed.class))).thenReturn(context);
                mockedStaticContext.when(Context::current).thenReturn(context);

                // The span builder shouldn't have any attributes.
                Assert.assertEquals(0, spanBuilderMock.getAttributes().size());
                Assert.assertFalse(spanBuilderMock.getAttributes().containsKey(attributeKeyObj));

                monitoringPipelineListener.onStartWithNewSpanStep(stepStartNode, workflowRun);

                // After the onStartWithNewSpanStep() call, the spanBuilder should contain the attribute.
                Assert.assertTrue(spanBuilderMock.getAttributes().containsKey(attributeKeyObj));
                Assert.assertEquals(attributeValueObj, spanBuilderMock.getAttributes().get(attributeKeyObj));
            }
        }
    }

    public static class NonParamTests {

        private static final String TEST_SPAN_NAME = "test-span";
        private static final String SH_STEP_SPAN_NAME = "sh-span";
        private static final FlowNode flowNode = Mockito.mock(FlowNode.class);
        private static SpanMock testSpan;

        @Before
        public void setup() throws IOException, InterruptedException {
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

        private void setupAttributesActionStubs(List<String> allowedIds) throws IOException, InterruptedException {
            Computer computer = Mockito.mock(Computer.class);

            Mockito.when(stepContext.get(Computer.class)).thenReturn(computer);

            // Computer AttributesAction stub.
            OpenTelemetryAttributesAction otelComputerAttributesAction = new OpenTelemetryAttributesAction();
            otelComputerAttributesAction.getAttributes().put(AttributeKey.stringKey("attribute.from.computer.action.applied"), "true");
            Mockito.when(computer.getAction(OpenTelemetryAttributesAction.class)).thenReturn(otelComputerAttributesAction);

            // Child AttributesAction stub.
            OpenTelemetryAttributesAction otelChildAttributesAction = new OpenTelemetryAttributesAction();

            for (String id : allowedIds) {
                otelChildAttributesAction.addSpanIdToInheritanceAllowedList(id);
            }

            otelChildAttributesAction.getAttributes().put(AttributeKey.stringKey("attribute.from.child.action.applied"), "true");
            Mockito.when(stepContext.get(OpenTelemetryAttributesAction.class)).thenReturn(otelChildAttributesAction);
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
                    AttributeKey<String> attributeKey = AttributeKey.stringKey("attribute.from." + component + ".action.applied");
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

            Assert.assertNotEquals(testSpan.getSpanContext().getSpanId(), shSpan.getSpanContext().getSpanId());

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
    }
}
