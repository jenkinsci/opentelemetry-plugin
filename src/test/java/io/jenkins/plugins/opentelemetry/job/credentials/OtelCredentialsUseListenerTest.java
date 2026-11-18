/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.credentials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.jenkins.plugins.opentelemetry.job.action.RunPhaseMonitoringAction;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Choice of the span, duplicates, cap and failures of {@link OtelCredentialsUseListener}, without Jenkins. */
public class OtelCredentialsUseListenerTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;
    private Tracer tracer;
    private Span rootSpan;
    private Span phaseSpan;
    private Run<?, ?> run;
    private boolean includeIds;

    @BeforeEach
    public void setUp() {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        tracer = tracerProvider.get("test");
        rootSpan = tracer.spanBuilder("BUILD root").startSpan();
        phaseSpan = tracer.spanBuilder("Phase: Run")
                .setParent(io.opentelemetry.context.Context.root().with(rootSpan))
                .startSpan();
        run = fakeRun(rootSpan, phaseSpan);
        includeIds = false;
    }

    @AfterEach
    public void tearDown() {
        tracerProvider.close();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Run<?, ?> fakeRun(Span root, Span phase) {
        Run run = mock(Run.class);
        when(run.getActions(MonitoringAction.class)).thenReturn(List.of(new MonitoringAction(root)));
        when(run.getActions(RunPhaseMonitoringAction.class)).thenReturn(List.of(new RunPhaseMonitoringAction(phase)));
        return run;
    }

    private OtelCredentialsUseListener listener(boolean recordEvents) {
        return new OtelCredentialsUseListener(() -> recordEvents, () -> includeIds, OtelTraceService::new);
    }

    private static IdCredentials credentials(String id, String type) {
        IdCredentials credentials = mock(IdCredentials.class);
        CredentialsDescriptor descriptor = mock(CredentialsDescriptor.class);
        when(descriptor.getDisplayName()).thenReturn(type);
        when(credentials.getDescriptor()).thenReturn(descriptor);
        when(credentials.getScope()).thenReturn(CredentialsScope.GLOBAL);
        when(credentials.getId()).thenReturn(id);
        return credentials;
    }

    private List<EventData> eventsOf(Span span) {
        tracerProvider.forceFlush();
        // The span is still open: read its events through the SDK
        return new ArrayList<>(
                ((io.opentelemetry.sdk.trace.ReadableSpan) span).toSpanData().getEvents());
    }

    @Test
    public void testFallsBackToThePhaseSpanWhenThereIsNoCurrentSpan() {
        listener(true).onUse(credentials("ze-id", "Username with password"), run);

        assertEquals(1, eventsOf(phaseSpan).size());
        assertEquals(0, eventsOf(rootSpan).size());
        EventData event = eventsOf(phaseSpan).get(0);
        assertEquals("jenkins.credentials.use", event.getName());
        assertEquals(
                "Username with password",
                event.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_CREDENTIALS_TYPE));
        assertEquals("GLOBAL", event.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_CREDENTIALS_SCOPE));
        assertNull(event.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_CREDENTIALS_ID));
    }

    @Test
    public void testUsesTheCurrentSpanOfTheRunsTrace() {
        Span stepSpan = tracer.spanBuilder("step")
                .setParent(io.opentelemetry.context.Context.root().with(phaseSpan))
                .startSpan();
        try (Scope ignored = stepSpan.makeCurrent()) {
            listener(true).onUse(credentials("ze-id", "Username with password"), run);
        }

        assertEquals(1, eventsOf(stepSpan).size());
        assertEquals(0, eventsOf(phaseSpan).size());
    }

    @Test
    public void testIgnoresTheCurrentSpanOfAnotherTrace() {
        Span otherTrace = tracer.spanBuilder("BUILD other").setNoParent().startSpan();
        try (Scope ignored = otherTrace.makeCurrent()) {
            listener(true).onUse(credentials("ze-id", "Username with password"), run);
        }

        assertEquals(0, eventsOf(otherTrace).size());
        assertEquals(1, eventsOf(phaseSpan).size());
    }

    @Test
    public void testIdIsRecordedOnlyWhenTheOptionIsOn() {
        includeIds = true;
        listener(true).onUse(credentials("ze-id", "Username with password"), run);

        assertEquals(
                "ze-id",
                eventsOf(phaseSpan).get(0).getAttributes().get(ExtendedJenkinsAttributes.JENKINS_CREDENTIALS_ID));
    }

    @Test
    public void testNothingWhenTheSettingIsOff() {
        listener(false).onUse(credentials("ze-id", "Username with password"), run);

        assertEquals(0, eventsOf(phaseSpan).size());
    }

    @Test
    public void testUsesByANodeOrAnItemDoNothing() {
        OtelTraceService service = mock(OtelTraceService.class);
        OtelCredentialsUseListener listener = new OtelCredentialsUseListener(() -> true, () -> true, () -> service);

        listener.onUse(credentials("ze-id", "Username with password"), mock(Node.class));
        listener.onUse(credentials("ze-id", "Username with password"), mock(Item.class));

        verifyNoInteractions(service);
        assertEquals(0, eventsOf(phaseSpan).size());
    }

    @Test
    public void testSameCredentialTwiceInOneSpanIsOneEventAndEventsPerSpanAreCapped() {
        OtelCredentialsUseListener listener = listener(true);
        IdCredentials same = credentials("same", "Username with password");
        listener.onUse(same, run);
        listener.onUse(same, run);
        for (int i = 0; i < 2 * OtelCredentialsUseListener.MAX_EVENTS_PER_SPAN; i++) {
            listener.onUse(credentials("cred-" + i, "Username with password"), run);
        }

        assertEquals(32, OtelCredentialsUseListener.MAX_EVENTS_PER_SPAN);
        assertEquals(
                OtelCredentialsUseListener.MAX_EVENTS_PER_SPAN,
                eventsOf(phaseSpan).size());
    }

    @Test
    public void testNoValidSpanDropsTheEventWithoutException() {
        Run<?, ?> runWithoutSpans = fakeRun(Span.getInvalid(), Span.getInvalid());
        when(runWithoutSpans.getActions(RunPhaseMonitoringAction.class)).thenReturn(List.of());

        listener(true).onUse(credentials("ze-id", "Username with password"), runWithoutSpans);

        assertEquals(0, eventsOf(phaseSpan).size());
    }

    @Test
    public void testFailureIsCaughtAndLoggedAtFine() {
        Logger logger = Logger.getLogger(OtelCredentialsUseListener.class.getName());
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        handler.setLevel(Level.ALL);
        Level previous = logger.getLevel();
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
        try {
            IdCredentials broken = credentials("broken", "type");
            when(broken.getScope()).thenThrow(new IllegalStateException("forced failure"));

            listener(true).onUse(broken, run);
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previous);
        }

        assertEquals(0, eventsOf(phaseSpan).size());
        assertTrue(records.stream().anyMatch(r -> r.getLevel() == Level.FINE && r.getThrown() != null));
        assertTrue(records.stream().noneMatch(r -> r.getLevel().intValue() >= Level.WARNING.intValue()));
    }
}
