/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rutledgepaulv.prune.Tree;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.jenkins.plugins.opentelemetry.job.PipelineCompileSpanListener;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import jenkins.plugins.git.GitSCMSource;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionListener;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.ClasspathAdder;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.TestExtension;

/**
 * The "Pipeline: compile" span covers the time between the creation of the flow execution of a build and its first
 * step: the Groovy compilation and the loading of the {@code @Library} libraries.
 */
public class JenkinsOtelPluginCompileSpanIntegrationTest extends BaseIntegrationTest {

    private static final String COMPILE = ExtendedJenkinsAttributes.JENKINS_PIPELINE_SPAN_COMPILE_NAME;
    private static final String RUN_PHASE = ExtendedJenkinsAttributes.JENKINS_JOB_SPAN_PHASE_RUN_NAME;

    /** What the thread that loads the libraries sees, recorded by {@link ProbeAdder}. */
    private static final List<Observation> OBSERVATIONS = new CopyOnWriteArrayList<>();

    /** When set, {@link ProbeAdder} holds the build that loads libraries until the test releases it. */
    private static final AtomicReference<CountDownLatch> GATE = new AtomicReference<>();

    private static final AtomicReference<CountDownLatch> ENTERED = new AtomicReference<>();

    record Observation(Span current, Span phase) {}

    @BeforeEach
    public void configureLibrary() throws Exception {
        OBSERVATIONS.clear();
        GATE.set(null);
        sampleRepo.init();
        sampleRepo.write("vars/greet.groovy", "def call() { echo 'hello from the library' }\n");
        sampleRepo.git("add", "vars/greet.groovy");
        sampleRepo.git("commit", "--message=library");
        LibraryConfiguration library =
                new LibraryConfiguration("lib", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())));
        library.setDefaultVersion("master");
        GlobalLibraries.get().setLibraries(List.of(library));
    }

    @Test
    public void testCompileSpanWithLibrary() throws Exception {
        JenkinsOpenTelemetryPluginConfiguration.get().setRecordCompileSpan(true);

        WorkflowRun run = build("@Library('lib@master') _\nnode { greet() }", Result.SUCCESS);

        Tree<SpanDataWrapper> trace = getBuildTrace();
        SpanData compile = onlySpan(trace, COMPILE);
        SpanData runPhase = onlySpan(trace, RUN_PHASE);
        assertEquals(runPhase.getSpanId(), compile.getParentSpanId());
        assertEquals(
                List.of("lib@master"),
                compile.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_PIPELINE_LIBRARIES));
        assertEquals(1L, compile.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_PIPELINE_LIBRARIES_COUNT));
        assertEquals(
                List.of("lib@master"),
                runPhase.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_PIPELINE_LIBRARIES));
        assertEquals(StatusCode.UNSET, compile.getStatus().getStatusCode());
        assertTrue(
                compile.getEndEpochNanos() <= firstStepStart(trace, compile),
                "The compile span must end before the first step starts");
        assertNoSpanHasParent(trace, compile);
        assertEquals(0, compileSpanListener().pendingBuilds(), "No state is left for " + run);
    }

    @Test
    public void testNoCompileSpanAndTheSameOtherSpansWhenTheSettingIsOff() throws Exception {
        String script = "@Library('lib@master') _\nnode { greet() }";
        assertFalse(JenkinsOpenTelemetryPluginConfiguration.get().isRecordCompileSpan());
        build(script, Result.SUCCESS);

        Tree<SpanDataWrapper> traceOff = getBuildTrace();
        assertTrue(spans(traceOff).stream().noneMatch(s -> s.getName().equals(COMPILE)));
        checkChainOfSpans(traceOff, "Agent", RUN_PHASE);
        assertNull(onlySpan(traceOff, RUN_PHASE)
                .getAttributes()
                .get(ExtendedJenkinsAttributes.JENKINS_PIPELINE_LIBRARIES));
        assertEquals(0, compileSpanListener().pendingBuilds());
        List<String> namesOff = names(traceOff);

        JenkinsOpenTelemetryPluginConfiguration.get().setRecordCompileSpan(true);
        build(script, Result.SUCCESS);

        List<String> namesOn = names(getBuildTrace(1));
        List<String> expectedOn = new ArrayList<>(namesOff);
        expectedOn.add(COMPILE);
        assertEquals(sorted(expectedOn), sorted(namesOn));
    }

    private static List<String> sorted(List<String> names) {
        return names.stream()
                .map(name -> name.startsWith("BUILD ") ? "BUILD" : name)
                .sorted()
                .collect(Collectors.toList());
    }

    @Test
    public void testCompileSpanWithoutLibraries() throws Exception {
        JenkinsOpenTelemetryPluginConfiguration.get().setRecordCompileSpan(true);

        build("node { echo 'x' }", Result.SUCCESS);

        SpanData compile = onlySpan(getBuildTrace(), COMPILE);
        assertEquals(List.of(), compile.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_PIPELINE_LIBRARIES));
        assertEquals(0L, compile.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_PIPELINE_LIBRARIES_COUNT));
    }

    @Test
    public void testCompileSpanOfABuildWithAMissingLibrary() throws Exception {
        JenkinsOpenTelemetryPluginConfiguration.get().setRecordCompileSpan(true);

        build("@Library('does-not-exist@master') _\nnode { echo 'x' }", Result.FAILURE);

        Tree<SpanDataWrapper> trace = getBuildTrace();
        SpanData compile = onlySpan(trace, COMPILE);
        assertEquals(StatusCode.ERROR, compile.getStatus().getStatusCode());
        assertEquals(onlySpan(trace, RUN_PHASE).getSpanId(), compile.getParentSpanId());
        assertEquals(0, compileSpanListener().pendingBuilds());

        build("node { echo 'second build' }", Result.SUCCESS);
        assertEquals(0, compileSpanListener().pendingBuilds());
    }

    @Test
    public void testCompileSpanOfABuildWithACompileError() throws Exception {
        JenkinsOpenTelemetryPluginConfiguration.get().setRecordCompileSpan(true);

        build("node { echo 'x' ", Result.FAILURE);

        Tree<SpanDataWrapper> trace = getBuildTrace();
        SpanData compile = onlySpan(trace, COMPILE);
        assertEquals(StatusCode.ERROR, compile.getStatus().getStatusCode());
        assertEquals(onlySpan(trace, RUN_PHASE).getSpanId(), compile.getParentSpanId());
        assertEquals(0, compileSpanListener().pendingBuilds());
    }

    /**
     * The {@code library} step has a step span of its own, which the compile span does not cover, and the libraries
     * it loads are not in the compile span.
     */
    @Test
    public void testLibraryStepHasItsOwnSpanOutsideTheCompileSpan() throws Exception {
        JenkinsOpenTelemetryPluginConfiguration.get().setRecordCompileSpan(true);

        build("library('lib@master')\nnode { echo 'x' }", Result.SUCCESS);

        Tree<SpanDataWrapper> trace = getBuildTrace();
        SpanData compile = onlySpan(trace, COMPILE);
        SpanData librarySpan = spans(trace).stream()
                .filter(s -> s.getName().contains("library"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No span for the library step in " + names(trace)));
        assertTrue(compile.getEndEpochNanos() <= librarySpan.getStartEpochNanos());
        assertEquals(List.of(), compile.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_PIPELINE_LIBRARIES));
    }

    /**
     * While the libraries load, the phase span is the open phase of the build but not the current span. The compile
     * span is made after the fact, so it is never current: not while the libraries of the second build load on the
     * same threads, and no span of any build, in any trace, has a compile span as parent.
     */
    @Test
    public void testNoCompileSpanIsCurrentOrParentOfASpanOfAnotherBuild() throws Exception {
        JenkinsOpenTelemetryPluginConfiguration.get().setRecordCompileSpan(true);

        build("@Library('lib@master') _\nnode { greet() }", Result.SUCCESS);
        build("@Library('lib@master') _\nnode { greet() }", Result.SUCCESS);

        Set<String> compileSpanIds = new HashSet<>();
        for (int traceIndex = 0; traceIndex < 2; traceIndex++) {
            Tree<SpanDataWrapper> trace = getBuildTrace(traceIndex);
            SpanData runPhase = onlySpan(trace, RUN_PHASE);
            SpanData compile = onlySpan(trace, COMPILE);
            assertEquals(runPhase.getSpanId(), compile.getParentSpanId());
            compileSpanIds.add(compile.getSpanId());
        }
        assertEquals(2, compileSpanIds.size());
        assertEquals(2, OBSERVATIONS.size());
        for (Observation observation : OBSERVATIONS) {
            String currentSpanId = observation.current().getSpanContext().getSpanId();
            assertFalse(compileSpanIds.contains(currentSpanId), "A compile span is current while libraries load");
            assertNotEquals(
                    observation.phase().getSpanContext().getSpanId(),
                    currentSpanId,
                    "The phase span is current while the libraries load");
        }
        for (SpanData span : InMemorySpanExporterProvider.LAST_CREATED_INSTANCE.getFinishedSpanItems()) {
            assertFalse(
                    compileSpanIds.contains(span.getParentSpanId()),
                    "'" + span.getName() + "' is a child of a compile span");
        }
    }

    /**
     * A flow execution that is resumed after a restart was not created in this session, so nothing was remembered for
     * it. While one build compiles (the listener holds one entry), a second build that is already running gets its
     * flow execution "resumed" and "running" again: it must not get another compile span and the entry of the build
     * that compiles must stay, and that build still gets its own span.
     */
    @Test
    public void testNoSecondCompileSpanForAResumedBuildWhileAnotherBuildCompiles() throws Exception {
        JenkinsOpenTelemetryPluginConfiguration.get().setRecordCompileSpan(true);
        WorkflowJob running = jenkinsRule.createProject(WorkflowJob.class, "compile-span-running");
        running.setDefinition(new CpsFlowDefinition("sleep 120", true));
        WorkflowRun inFlight = running.scheduleBuild2(0).waitForStart();
        jenkinsRule.waitForMessage("Sleeping for", inFlight);
        String inFlightTraceId = inFlight.getAction(MonitoringAction.class).getTraceId();
        assertEquals(1, compileSpansOfTrace(inFlightTraceId));
        GATE.set(new CountDownLatch(1));
        ENTERED.set(new CountDownLatch(1));
        WorkflowJob job = jenkinsRule.createProject(WorkflowJob.class, "compile-span-blocked");
        job.setDefinition(new CpsFlowDefinition("@Library('lib@master') _\nnode { greet() }", true));
        QueueTaskFuture<WorkflowRun> compiling = job.scheduleBuild2(0);
        try {
            assertTrue(ENTERED.get().await(60, TimeUnit.SECONDS), "The libraries of the second build did not load");
            assertEquals(1, compileSpanListener().pendingBuilds());

            FlowExecutionListener.fireResumed(inFlight.getExecution());
            FlowExecutionListener.fireRunning(inFlight.getExecution());

            assertEquals(1, compileSpansOfTrace(inFlightTraceId));
            assertEquals(1, compileSpanListener().pendingBuilds());
        } finally {
            GATE.get().countDown();
            inFlight.doStop();
        }
        WorkflowRun second = jenkinsRule.assertBuildStatusSuccess(compiling);
        jenkinsRule.waitForCompletion(inFlight);
        assertEquals(
                1, compileSpansOfTrace(second.getAction(MonitoringAction.class).getTraceId()));
        assertEquals(0, compileSpanListener().pendingBuilds());
    }

    /**
     * A failure of the compile span listener must not change the rest of the trace: the phases of the build are still
     * ended and started in order. The map of the builds that compile fails when the listener takes an entry out of it,
     * which happens when the build completes without its flow execution running (a missing library).
     */
    @Test
    public void testFailureOfTheListenerLeavesTheRestOfTheTraceCorrect() throws Exception {
        JenkinsOpenTelemetryPluginConfiguration.get().setRecordCompileSpan(true);
        PipelineCompileSpanListener listener = compileSpanListener();
        Field field = PipelineCompileSpanListener.class.getDeclaredField("compileStarts");
        field.setAccessible(true);
        Object original = field.get(listener);
        Map<Object, Object> failing = new ConcurrentHashMap<>() {
            @Override
            public Object remove(Object key) {
                throw new IllegalStateException("forced failure of the compile listener");
            }
        };
        field.set(listener, failing);
        try {
            build("@Library('does-not-exist@master') _\nnode { echo 'x' }", Result.FAILURE);
        } finally {
            field.set(listener, original);
        }

        Tree<SpanDataWrapper> trace = getBuildTrace();
        assertEquals(
                List.of("BUILD", "Phase: Finalise", "Phase: Run", "Phase: Start"),
                names(trace).stream()
                        .map(name -> name.startsWith("BUILD ") ? "BUILD" : name)
                        .sorted()
                        .collect(Collectors.toList()));
        SpanData root = trace.breadthFirstStreamNodes()
                .map(n -> n.getData().spanData)
                .filter(s -> s.getName().startsWith("BUILD "))
                .findFirst()
                .orElseThrow();
        for (SpanData phase : spans(trace)) {
            if (phase.getName().startsWith("Phase: ")) {
                assertEquals(root.getSpanId(), phase.getParentSpanId(), phase.getName());
            }
        }
        assertTrue(
                onlySpan(trace, RUN_PHASE).getEndEpochNanos()
                        <= onlySpan(trace, "Phase: Finalise").getStartEpochNanos(),
                "Phase: Run must end before Phase: Finalise starts");
    }

    private long compileSpansOfTrace(String traceId) {
        jenkinsControllerOpenTelemetry
                .getOpenTelemetrySdk()
                .getSdkTracerProvider()
                .forceFlush()
                .join(1, TimeUnit.SECONDS);
        return InMemorySpanExporterProvider.LAST_CREATED_INSTANCE.getFinishedSpanItems().stream()
                .filter(s -> s.getTraceId().equals(traceId))
                .filter(s -> s.getName().equals(COMPILE))
                .count();
    }

    private WorkflowRun build(String script, Result expected) throws Exception {
        WorkflowJob job =
                jenkinsRule.createProject(WorkflowJob.class, "compile-span-" + jobNameSuffix.incrementAndGet());
        job.setDefinition(new CpsFlowDefinition(script, true));
        return jenkinsRule.assertBuildStatus(expected, job.scheduleBuild2(0));
    }

    private static PipelineCompileSpanListener compileSpanListener() {
        return PipelineCompileSpanListener.get();
    }

    private static List<SpanData> spans(Tree<SpanDataWrapper> trace) {
        return trace.breadthFirstStreamNodes().map(n -> n.getData().spanData).collect(Collectors.toList());
    }

    private static List<String> names(Tree<SpanDataWrapper> trace) {
        return spans(trace).stream().map(SpanData::getName).collect(Collectors.toList());
    }

    private static SpanData onlySpan(Tree<SpanDataWrapper> trace, String name) {
        List<SpanData> found =
                spans(trace).stream().filter(s -> s.getName().equals(name)).collect(Collectors.toList());
        assertEquals(1, found.size(), "Spans named '" + name + "' in " + names(trace));
        return found.get(0);
    }

    private static long firstStepStart(Tree<SpanDataWrapper> trace, SpanData compile) {
        String runPhaseId = onlySpan(trace, RUN_PHASE).getSpanId();
        return spans(trace).stream()
                .filter(s -> !s.getSpanId().equals(compile.getSpanId()))
                .filter(s -> !s.getName().equals(RUN_PHASE))
                .filter(s -> !s.getName().startsWith(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX))
                .filter(s -> !s.getName().startsWith("Phase: "))
                .mapToLong(SpanData::getStartEpochNanos)
                .min()
                .orElseThrow(() -> new AssertionError("No step span below " + runPhaseId));
    }

    private static void assertNoSpanHasParent(Tree<SpanDataWrapper> trace, SpanData parent) {
        spans(trace)
                .forEach(s -> assertNotEquals(
                        parent.getSpanId(),
                        s.getParentSpanId(),
                        "'" + s.getName() + "' is a child of " + parent.getName()));
    }

    /** Records the span context of the thread that loads the libraries. */
    @TestExtension
    public static class ProbeAdder extends ClasspathAdder {
        @NonNull
        @Override
        public List<Addition> add(
                @NonNull CpsFlowExecution execution,
                @NonNull List<String> libraries,
                @NonNull HashMap<String, Boolean> changelogs)
                throws Exception {
            Run<?, ?> run = (Run<?, ?>) execution.getOwner().getExecutable();
            OBSERVATIONS.add(
                    new Observation(Span.current(), OtelTraceService.get().getSpan(run)));
            CountDownLatch gate = GATE.get();
            if (gate != null) {
                ENTERED.get().countDown();
                gate.await(60, TimeUnit.SECONDS);
            }
            return List.of();
        }
    }
}
