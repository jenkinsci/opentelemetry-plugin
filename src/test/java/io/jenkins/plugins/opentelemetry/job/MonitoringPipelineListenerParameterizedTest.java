/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import static com.google.common.base.Verify.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import hudson.ExtensionList;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class MonitoringPipelineListenerParameterizedTest {

    @RegisterExtension
    public static final JenkinsRule jenkinsRule = new JenkinsRule();

    private static final StepContext stepContext = Mockito.mock(StepContext.class);
    private static final OtelTraceService otelTraceService = Mockito.mock(OtelTraceService.class);
    private static final WorkflowRun workflowRun = Mockito.mock(WorkflowRun.class);
    private static final String START_NODE_ROOT_SPAN_NAME = "root-span";
    private static final String WITH_NEW_SPAN_NAME = "with-new-span";
    private final SpanBuilderMock spanBuilderMock = Mockito.spy(new SpanBuilderMock(WITH_NEW_SPAN_NAME));
    private MonitoringPipelineListener monitoringPipelineListener;

    @BeforeAll
    public static void commonSetup() throws IOException, InterruptedException {
        // Jenkins must have been initialized.
        org.junit.jupiter.api.Assertions.assertNotNull(Jenkins.getInstanceOrNull());

        Mockito.when(stepContext.get(WorkflowRun.class)).thenReturn(workflowRun);
    }

    public static Collection<Object[]> argumentsActionScenarios() {
        return Arrays.asList(new Object[][] {
            {"with.new.span.boolean", AttributeKey.booleanKey("with.new.span.boolean"), true},
            {"with.new.span.string", AttributeKey.stringKey("with.new.span.string"), "true"},
            {"with.new.span.long", AttributeKey.longKey("with.new.span.long"), 2L},
            {"with.new.span.double", AttributeKey.doubleKey("with.new.span.double"), 2.22},
        });
    }

    @BeforeEach
    public void setup() {
        ExtensionList<JenkinsControllerOpenTelemetry> jenkinsOpenTelemetries =
                jenkinsRule.getInstance().getExtensionList(JenkinsControllerOpenTelemetry.class);
        verify(
                jenkinsOpenTelemetries.size() == 1,
                "Number of jenkinsControllerOpenTelemetrys: %s",
                jenkinsOpenTelemetries.size());
        JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry = Mockito.spy(jenkinsOpenTelemetries.get(0));

        Tracer tracer = Mockito.spy(new TracerMock());

        Mockito.when(tracer.spanBuilder(WITH_NEW_SPAN_NAME)).thenReturn(spanBuilderMock);

        Mockito.when(jenkinsControllerOpenTelemetry.getDefaultTracer()).thenReturn(tracer);

        monitoringPipelineListener = new MonitoringPipelineListener();
        monitoringPipelineListener.jenkinsControllerOpenTelemetry = jenkinsControllerOpenTelemetry;

        Assertions.assertNull(monitoringPipelineListener.getTracer());
        // postConstruct() calls the getDefaultTracer() method which needs to be stubbed in advance before using the
        // tracer.
        // Manually invoke the postConstruct() method to re-apply the @PostConstruct logic.
        monitoringPipelineListener.postConstruct();

        Assertions.assertNotNull(monitoringPipelineListener.getTracer());

        monitoringPipelineListener.setOpenTelemetryTracerService(otelTraceService);
    }

    @ParameterizedTest
    @MethodSource("argumentsActionScenarios")
    public void testOnStartWithNewSpanStep(String attributeKeyName, Object attributeKeyObj, Object attributeValueObj) {

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
        Assertions.assertNotNull(monitoringPipelineListener.getTracerService().getSpan(workflowRun));

        SpanMock newSpan = Mockito.spy(new SpanMock(WITH_NEW_SPAN_NAME));
        Mockito.when(monitoringPipelineListener
                        .getTracer()
                        .spanBuilder(WITH_NEW_SPAN_NAME)
                        .startSpan())
                .thenReturn(newSpan);

        try (MockedStatic<Span> mockedStaticSpan = mockStatic(Span.class);
                MockedStatic<Context> mockedStaticContext = mockStatic(Context.class)) {
            // Span.current() should return the mocked span.
            mockedStaticSpan.when(Span::current).thenReturn(rootSpan);
            Assertions.assertEquals(rootSpan, Span.current());

            mockedStaticSpan.when(() -> Span.fromContext(any())).thenReturn(rootSpan);

            Context context = Mockito.mock(Context.class);
            when(context.with(any(ImplicitContextKeyed.class))).thenReturn(context);
            mockedStaticContext.when(Context::current).thenReturn(context);

            // The span builder shouldn't have any attributes.
            Assertions.assertEquals(0, spanBuilderMock.getAttributes().size());
            Assertions.assertFalse(spanBuilderMock.getAttributes().containsKey(attributeKeyObj));

            monitoringPipelineListener.onStartWithNewSpanStep(stepStartNode, workflowRun);

            // After the onStartWithNewSpanStep() call, the spanBuilder should contain the attribute.
            Assertions.assertTrue(spanBuilderMock.getAttributes().containsKey(attributeKeyObj));
            Assertions.assertEquals(
                    attributeValueObj, spanBuilderMock.getAttributes().get(attributeKeyObj));
        }
    }
}
