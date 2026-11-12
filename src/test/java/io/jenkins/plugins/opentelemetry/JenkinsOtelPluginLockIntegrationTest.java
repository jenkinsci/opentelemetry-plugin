/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.job.action.FlowNodeMonitoringAction;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jenkins.plugins.lockableresources.LockStep;
import org.jenkins.plugins.lockableresources.LockableResourcesManager;
import org.jenkins.plugins.lockableresources.actions.LockedResourcesBuildAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The {@code lock} step, listed in the {@code blockSteps} setting, gets a span, and a child span for the time that the
 * build waited for the resource.
 */
public class JenkinsOtelPluginLockIntegrationTest extends BaseIntegrationTest {

    private static final long LONG_WAIT_MILLIS = 1000;
    private static final AttributeKey<Long> WAIT_DURATION =
            AttributeKey.longKey("jenkins.pipeline.step.lock.wait.duration");
    private static final AttributeKey<String> RESOURCE = AttributeKey.stringKey("jenkins.pipeline.step.lock.resource");
    private static final AttributeKey<String> LABEL = AttributeKey.stringKey("jenkins.pipeline.step.lock.label");
    private static final String SHELL = "if (isUnix()) { sh label: 'ze-shell', script: 'echo ze-echo' } "
            + "else { bat label: 'ze-shell', script: 'echo ze-echo' }";

    @Test
    public void testNoLockSpanWhenLockIsNotInBlockSteps() throws Exception {
        WorkflowRun run = runPipeline("lock('ze-resource') { %s }".formatted(SHELL), Result.SUCCESS);

        assertEquals(List.of(), spansNamed("lock"));
        assertEquals(List.of(), spansNamed("lock: wait"));
        assertEquals(1, spansNamed("ze-shell").size());
        assertEquals(8, exportedSpans().size(), "spans of the pipeline before the change");
        assertNotNull(run);
    }

    @Test
    public void testUncontendedLockHasASpanAndNoWaitSpan() throws Exception {
        configureBlockSteps("lock");

        runPipeline("lock('ze-resource') { %s }".formatted(SHELL), Result.SUCCESS);

        SpanData lock = onlySpan("lock");
        assertEquals("ze-resource", lock.getAttributes().get(RESOURCE));
        assertEquals(StatusCode.OK, lock.getStatus().getStatusCode());
        assertNoLongWait(spansNamed("lock: wait"));
        Long waitDuration = lock.getAttributes().get(WAIT_DURATION);
        assertTrue(waitDuration == null || waitDuration < LONG_WAIT_MILLIS, "wait duration of an uncontended lock");
        assertEquals(lock.getSpanId(), onlySpan("ze-shell").getParentSpanId());
    }

    @Test
    public void testContendedLockHasAWaitSpanThatCoversTheWait() throws Exception {
        configureBlockSteps("lock");
        jenkinsRule.createOnlineSlave();
        WorkflowRun first = startHolder();
        waitForLock("ze-resource");
        WorkflowRun second = start("lock('ze-resource') { %s }".formatted(SHELL));
        waitForOuterStart(second, "lock");
        Thread.sleep(2500);
        release();
        jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(first));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(second));
        flush();

        SpanData lock = spansOfBuild(second, "lock").get(0);
        SpanData wait = spansOfBuild(second, "lock: wait").get(0);
        SpanData shell = spansOfBuild(second, "ze-shell").get(0);
        assertEquals(lock.getSpanId(), wait.getParentSpanId());
        long waited = durationMillis(wait);
        assertTrue(waited >= 2000, "wait of " + waited + " ms");
        assertEquals(waited, lock.getAttributes().get(WAIT_DURATION));
        // The pause of the lock step has a start time in milliseconds, the span a start time in nanoseconds
        assertTrue(TimeUnit.NANOSECONDS.toMillis(wait.getStartEpochNanos())
                >= TimeUnit.NANOSECONDS.toMillis(lock.getStartEpochNanos()));
        assertTrue(wait.getEndEpochNanos() <= shell.getStartEpochNanos(), "the wait ends before the body");
        assertTrue(wait.getEndEpochNanos() <= lock.getEndEpochNanos());
        assertEquals(lock.getSpanId(), shell.getParentSpanId());
        assertEquals(1, spansOfBuild(first, "lock").size());
        assertNoLongWait(spansOfBuild(first, "lock: wait"));
    }

    @Test
    public void testNoSpanExistsForTheWaitWhileTheBuildIsWaiting() throws Exception {
        configureBlockSteps("lock");
        jenkinsRule.createOnlineSlave();
        WorkflowRun first = startHolder();
        waitForLock("ze-resource");
        WorkflowRun second = start("lock('ze-resource') { %s }".formatted(SHELL));
        FlowNode outerStart = waitForOuterStart(second, "lock");
        Thread.sleep(2000);

        assertTrue(second.isBuilding());
        assertTrue(org.jenkinsci.plugins.workflow.support.actions.PauseAction.isPaused(outerStart));
        List<FlowNodeMonitoringAction> actions = outerStart.getActions(FlowNodeMonitoringAction.class);
        assertEquals(1, actions.size(), "only the lock span is on the node while it waits");
        assertFalse(actions.get(0).hasEnded());
        assertNoLongWait(spansNamed("lock: wait"));

        release();
        jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(first));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(second));
        flush();
        assertEquals(1, spansOfBuild(second, "lock: wait").size());
    }

    @Test
    public void testSkipIfLockedWithABusyLockEndsTheSpanWithoutBodyNorWait() throws Exception {
        configureBlockSteps("lock");
        jenkinsRule.createOnlineSlave();
        WorkflowRun first = startHolder();
        waitForLock("ze-resource");
        WorkflowRun second = start("lock(resource: 'ze-resource', skipIfLocked: true) { %s }".formatted(SHELL));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(second));
        release();
        jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(first));
        flush();

        assertEquals(1, spansOfBuild(second, "lock").size());
        assertEquals(
                StatusCode.OK, spansOfBuild(second, "lock").get(0).getStatus().getStatusCode());
        assertEquals(List.of(), spansOfBuild(second, "lock: wait"));
        assertEquals(0, spansOfBuild(second, "ze-shell").size());
        jenkinsRule.assertLogNotContains("Exception", second);
        assertAllSpansEnded(second);
    }

    @Test
    public void testSkippedLockFollowedByAStepInAStageEndsItsSpan() throws Exception {
        assertSkippedLockSpanEnds("node() { stage('ze-stage') { %s; sh label: 'ze-after', script: 'echo after' } }");
    }

    @Test
    public void testSkippedLockFollowedByAStepAtTopLevelEndsItsSpan() throws Exception {
        assertSkippedLockSpanEnds("node() { %s; sh label: 'ze-after', script: 'echo after' }");
    }

    private void assertSkippedLockSpanEnds(String pipelineFormat) throws Exception {
        configureBlockSteps("lock");
        jenkinsRule.createOnlineSlave();
        WorkflowRun first = startHolder();
        waitForLock("ze-resource");
        String skipped = "lock(resource: 'ze-resource', skipIfLocked: true) { sh label: 'ze-body', script: 'echo b' }";
        WorkflowRun second = startRaw(pipelineFormat.formatted(skipped));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(second));
        release();
        jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(first));
        flush();

        List<SpanData> locks = spansOfBuild(second, "lock");
        assertEquals(1, locks.size());
        assertEquals(StatusCode.OK, locks.get(0).getStatus().getStatusCode());
        assertEquals(List.of(), spansOfBuild(second, "lock: wait"));
        assertEquals(0, spansOfBuild(second, "ze-body").size());
        List<SpanData> after = spansOfBuild(second, "ze-after");
        assertEquals(1, after.size());
        assertTrue(after.get(0).getParentSpanId().compareTo(locks.get(0).getSpanId()) != 0, "not under the lock span");
        assertTrue(locks.get(0).getEndEpochNanos() <= after.get(0).getStartEpochNanos());
        jenkinsRule.assertLogNotContains("Exception", second);
        assertAllSpansEnded(second);
    }

    @Test
    public void testAcquireTimeoutEndsTheLockSpanAndTheWait() throws Exception {
        // The timeout argument exists since a later release of the lock plugin than the one that the BOM manages
        assumeTrue(
                Arrays.stream(LockStep.class.getFields())
                        .anyMatch(field -> "timeoutForAllocateResource".equals(field.getName())),
                "the lock plugin on the test classpath has no timeoutForAllocateResource argument");
        configureBlockSteps("lock");
        jenkinsRule.createOnlineSlave();
        WorkflowRun first = startHolder();
        waitForLock("ze-resource");
        WorkflowRun second = start("lock(resource: 'ze-resource', timeoutForAllocateResource: 2, "
                + "timeoutUnit: 'SECONDS') { %s }".formatted(SHELL));
        jenkinsRule.waitForCompletion(second);
        release();
        jenkinsRule.waitForCompletion(first);
        flush();

        // The build fails and the lock span has the error status with the message of the lock plugin
        assertEquals(Result.FAILURE, second.getResult());
        List<SpanData> locks = spansOfBuild(second, "lock");
        assertEquals(1, locks.size());
        assertEquals(StatusCode.ERROR, locks.get(0).getStatus().getStatusCode());
        assertTrue(locks.get(0).getStatus().getDescription().contains("timed out waiting for resource allocation"));
        assertEquals(0, spansOfBuild(second, "ze-shell").size());
        List<SpanData> waits = spansOfBuild(second, "lock: wait");
        assertEquals(1, waits.size());
        assertTrue(waits.get(0).getEndEpochNanos() <= locks.get(0).getEndEpochNanos());
        assertTrue(durationMillis(waits.get(0)) >= 1500);
        assertAllSpansEnded(second);
    }

    /** Reaches the same path as the acquire timeout, and works with the lock plugin that the BOM manages. */
    @Test
    public void testAbortWhileWaitingEndsTheLockSpanAndTheWait() throws Exception {
        configureBlockSteps("lock");
        jenkinsRule.createOnlineSlave();
        WorkflowRun first = startHolder();
        waitForLock("ze-resource");
        WorkflowRun second = start("lock('ze-resource') { %s }".formatted(SHELL));
        waitForOuterStart(second, "lock");
        Thread.sleep(2000);
        second.getOneOffExecutor().interrupt(Result.ABORTED);
        jenkinsRule.assertBuildStatus(Result.ABORTED, jenkinsRule.waitForCompletion(second));
        release();
        jenkinsRule.assertBuildStatus(Result.SUCCESS, jenkinsRule.waitForCompletion(first));
        flush();

        List<SpanData> locks = spansOfBuild(second, "lock");
        assertEquals(1, locks.size());
        assertNotEquals(StatusCode.OK, locks.get(0).getStatus().getStatusCode());
        assertEquals(0, spansOfBuild(second, "ze-shell").size());
        List<SpanData> waits = spansOfBuild(second, "lock: wait");
        assertEquals(1, waits.size());
        assertEquals(locks.get(0).getSpanId(), waits.get(0).getParentSpanId());
        assertTrue(waits.get(0).getEndEpochNanos() <= locks.get(0).getEndEpochNanos());
        long waited = durationMillis(waits.get(0));
        assertTrue(waited >= 1500, "wait of " + waited + " ms");
        assertEquals(waited, locks.get(0).getAttributes().get(WAIT_DURATION));
        assertAllSpansEnded(second);
    }

    @Test
    public void testLabelLockSetsOnlyTheLabelAndNoRealResourceNameIsInTheTrace() throws Exception {
        configureBlockSteps("lock");
        LockableResourcesManager.get().createResourceWithLabel("ze-planted-name-alpha", "ze-label");
        LockableResourcesManager.get().createResourceWithLabel("ze-planted-name-beta", "ze-label");

        WorkflowRun run = runPipeline("lock(label: 'ze-label', quantity: 1) { %s }".formatted(SHELL), Result.SUCCESS);

        SpanData lock = onlySpan("lock");
        assertEquals("ze-label", lock.getAttributes().get(LABEL));
        assertNull(lock.getAttributes().get(RESOURCE));
        assertNoLongWait(spansNamed("lock: wait"));
        // Settled: the real names are not read. They are in no span name, attribute value or event of the trace.
        for (SpanData span : exportedSpans()) {
            String everything = span.getName() + span.getAttributes() + span.getEvents();
            assertFalse(everything.contains("ze-planted-name"), "real resource name in span " + span.getName());
        }
        // The lock plugin keeps the names per run and only while they are held: nothing is left after the build
        LockedResourcesBuildAction action = run.getAction(LockedResourcesBuildAction.class);
        assertTrue(action == null || action.getCurrentUsedResourceNames().isEmpty());
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    private void configureBlockSteps(String blockSteps) {
        JenkinsOpenTelemetryPluginConfiguration.get().setBlockSteps(blockSteps);
    }

    private File releaseFile;

    /** A build that holds the resource until {@link #release()} creates the file it waits for. */
    private WorkflowRun startHolder() throws Exception {
        releaseFile = new File(jenkinsRule.jenkins.getRootDir(), "release-lock-" + jobNameSuffix.incrementAndGet());
        return start("lock('ze-resource') { waitUntil { fileExists('%s') } }"
                .formatted(releaseFile.getAbsolutePath().replace('\\', '/')));
    }

    /** A test that fails before it releases the holder would leave the build running, and the teardown would wait. */
    @AfterEach
    public void releaseHolderInAnyCase() throws Exception {
        if (releaseFile != null && !releaseFile.exists()) {
            releaseFile.createNewFile();
        }
    }

    private void release() throws Exception {
        assertTrue(releaseFile.createNewFile());
    }

    private WorkflowRun start(String body) throws Exception {
        return startRaw("node() { stage('ze-stage') { %s } }".formatted(body));
    }

    private WorkflowRun startRaw(String pipeline) throws Exception {
        WorkflowJob job = jenkinsRule.createProject(WorkflowJob.class, "test-lock-" + jobNameSuffix.incrementAndGet());
        job.setDefinition(new CpsFlowDefinition(pipeline, true));
        return job.scheduleBuild2(0).waitForStart();
    }

    private WorkflowRun runPipeline(String body, Result expected) throws Exception {
        jenkinsRule.createOnlineSlave();
        WorkflowRun run = start(body);
        jenkinsRule.assertBuildStatus(expected, jenkinsRule.waitForCompletion(run));
        flush();
        return run;
    }

    private void waitForLock(String resource) throws Exception {
        long deadline = System.currentTimeMillis() + 120_000;
        while (LockableResourcesManager.get().fromName(resource) == null
                || !LockableResourcesManager.get().fromName(resource).isLocked()) {
            assertTrue(System.currentTimeMillis() < deadline, "resource never locked");
            Thread.sleep(100);
        }
    }

    private FlowNode waitForOuterStart(WorkflowRun run, String functionName) throws Exception {
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            FlowExecution execution = run.getExecution();
            // The heads are not set for a moment after the execution exists
            if (execution != null
                    && !execution.getCurrentHeads().isEmpty()
                    && !execution.getCurrentHeads().contains(null)) {
                for (FlowNode node : new DepthFirstScanner().allNodes(execution)) {
                    if (node instanceof StepStartNode start
                            && start.getDescriptor() != null
                            && functionName.equals(start.getDescriptor().getFunctionName())
                            && start.getAction(org.jenkinsci.plugins.workflow.actions.BodyInvocationAction.class)
                                    == null) {
                        return node;
                    }
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No start node of " + functionName);
    }

    private void flush() throws Exception {
        jenkinsControllerOpenTelemetry
                .getOpenTelemetrySdk()
                .getSdkTracerProvider()
                .forceFlush()
                .join(5, TimeUnit.SECONDS);
    }

    private List<SpanData> exportedSpans() {
        return InMemorySpanExporterProvider.LAST_CREATED_INSTANCE.getFinishedSpanItems();
    }

    private List<SpanData> spansNamed(String name) {
        return exportedSpans().stream().filter(s -> name.equals(s.getName())).collect(Collectors.toList());
    }

    private SpanData onlySpan(String name) {
        List<SpanData> found = spansNamed(name);
        assertEquals(1, found.size(), "spans named '" + name + "'");
        return found.get(0);
    }

    /** The spans of the trace of the build: the build's root span is the one that carries the build number. */
    private List<SpanData> spansOfBuild(WorkflowRun run, String name) {
        String traceId = exportedSpans().stream()
                .filter(s -> s.getName().startsWith("BUILD ")
                        && s.getName().contains(run.getParent().getName()))
                .map(SpanData::getTraceId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No root span of " + run));
        return exportedSpans().stream()
                .filter(s -> traceId.equals(s.getTraceId()) && name.equals(s.getName()))
                .collect(Collectors.toList());
    }

    private void assertAllSpansEnded(WorkflowRun run) throws Exception {
        for (FlowNode node : new DepthFirstScanner().allNodes(run.getExecution())) {
            for (FlowNodeMonitoringAction action : node.getActions(FlowNodeMonitoringAction.class)) {
                assertTrue(action.hasEnded(), "span still open on " + node.getDisplayName());
            }
        }
    }

    /**
     * The lock step records a short pause even when the resource is free, and the plugin reports a pause of 50 ms or
     * more as a wait. On a loaded machine that pause can pass 50 ms, so the tests of "no wait" ignore a wait that is
     * far shorter than the waits that the tests make.
     */
    private static void assertNoLongWait(List<SpanData> waits) {
        assertEquals(
                List.of(),
                waits.stream()
                        .filter(wait -> durationMillis(wait) >= LONG_WAIT_MILLIS)
                        .collect(Collectors.toList()),
                "wait spans of one second or more");
    }

    private static long durationMillis(SpanData span) {
        return TimeUnit.NANOSECONDS.toMillis(span.getEndEpochNanos() - span.getStartEpochNanos());
    }
}
