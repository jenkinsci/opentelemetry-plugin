/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.github.rutledgepaulv.prune.Tree;
import hudson.AbortException;
import hudson.model.Result;
import hudson.util.Secret;
import io.jenkins.plugins.opentelemetry.job.action.FlowNodeMonitoringAction;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.Serial;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Block steps listed in the {@code blockSteps} setting get one span per use, under their stage or agent, and
 * everything that runs inside the block is a child of that span.
 */
public class JenkinsOtelPluginBlockStepsIntegrationTest extends BaseIntegrationTest {

    private static final String PLANTED_SECRET = "planted-secret-9d2e4b17";
    private static final String STEP_TIME = "jenkins.pipeline.step.timeout.time";
    private static final AttributeKey<Boolean> TIMEOUT_EXCEEDED =
            AttributeKey.booleanKey("jenkins.pipeline.step.timeout.exceeded");
    private static final AttributeKey<Long> TIMEOUT_TIME = AttributeKey.longKey(STEP_TIME);
    private static final AttributeKey<String> TIMEOUT_UNIT =
            AttributeKey.stringKey("jenkins.pipeline.step.timeout.unit");
    private static final AttributeKey<Boolean> TIMEOUT_ACTIVITY =
            AttributeKey.booleanKey("jenkins.pipeline.step.timeout.activity");
    private static final AttributeKey<Long> ENV_COUNT = AttributeKey.longKey("jenkins.pipeline.step.env.count");
    private static final Set<String> BLOCK_STEP_SPAN_NAMES = Set.of("timeout", "withEnv", "noBodyBlockTestStep");

    private static final String SHELL = "if (isUnix()) { sh label: 'ze-shell', script: 'echo ze-echo' } "
            + "else { bat label: 'ze-shell', script: 'echo ze-echo' }";

    @Test
    public void testNothingChangesWhenBlockStepsIsEmpty() throws Exception {
        String pipeline = """
                node() {
                    stage('ze-stage') {
                        timeout(time: 1, unit: 'MINUTES') {
                            withEnv(['ZE_VAR=ze-value']) {
                                %s
                            }
                        }
                    }
                }
                """.formatted(SHELL);

        runPipeline(pipeline, Result.SUCCESS);

        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpansUpToRunPhase(spans, "ze-shell", "Stage: ze-stage", ExtendedJenkinsAttributes.AGENT_UI);
        assertEquals(List.of(), blockSpanNames(), "block spans");
        // 7 spans before the block steps existed: root, Phase: Start, Phase: Run, Phase: Finalise, agent, stage, sh
        // plus the agent allocation span
        assertEquals(DEFAULT_SPAN_COUNT, exportedSpans().size());
    }

    @Test
    public void testBlockSpansAreParentsOfTheStepsInsideThem() throws Exception {
        configureBlockSteps("timeout,withEnv");
        String pipeline = """
                node() {
                    stage('ze-stage') {
                        timeout(time: 90, unit: 'SECONDS', activity: true) {
                            withEnv(['ZE_VAR=ze-value', 'OTHER_VAR=other']) {
                                %s
                            }
                        }
                    }
                }
                """.formatted(SHELL);

        runPipeline(pipeline, Result.SUCCESS);

        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpansUpToRunPhase(
                spans, "ze-shell", "withEnv", "timeout", "Stage: ze-stage", ExtendedJenkinsAttributes.AGENT_UI);
        SpanData timeout = onlySpan("timeout");
        assertEquals(90L, timeout.getAttributes().get(TIMEOUT_TIME));
        assertEquals("SECONDS", timeout.getAttributes().get(TIMEOUT_UNIT));
        assertEquals(Boolean.TRUE, timeout.getAttributes().get(TIMEOUT_ACTIVITY));
        assertEquals(Boolean.FALSE, timeout.getAttributes().get(TIMEOUT_EXCEEDED));
        assertEquals(StatusCode.OK, timeout.getStatus().getStatusCode());
        assertNotNull(timeout.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_STEP_ID));
        assertEquals(DEFAULT_SPAN_COUNT + 2, exportedSpans().size());
    }

    @Test
    public void testTimeoutThatFiresIsMarkedOnItsSpan() throws Exception {
        configureBlockSteps("timeout");
        String pipeline = """
                node() {
                    stage('ze-stage') {
                        timeout(time: 1, unit: 'SECONDS') {
                            sleep 60
                        }
                    }
                }
                """;

        runPipeline(pipeline, Result.ABORTED);

        getBuildTrace();
        SpanData timeout = onlySpan("timeout");
        assertEquals(Boolean.TRUE, timeout.getAttributes().get(TIMEOUT_EXCEEDED));
        assertEquals(1L, timeout.getAttributes().get(TIMEOUT_TIME));
        assertEquals("SECONDS", timeout.getAttributes().get(TIMEOUT_UNIT));
        assertEquals(Boolean.FALSE, timeout.getAttributes().get(TIMEOUT_ACTIVITY));
        // Same status as any step interrupted by a cause that is not in the "status unset" list
        assertEquals(StatusCode.ERROR, timeout.getStatus().getStatusCode());
        List<String> causes = timeout.getAttributes().get(ExtendedJenkinsAttributes.JENKINS_STEP_INTERRUPTION_CAUSES);
        assertNotNull(causes);
        assertTrue(causes.get(0).startsWith("ExceededTimeout"), causes.toString());
    }

    @Test
    public void testWithEnvSpanHasOnlyTheNumberOfOverrides() throws Exception {
        configureBlockSteps("withEnv");
        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(
                        Domain.global(),
                        List.of(new StringCredentialsImpl(
                                CredentialsScope.GLOBAL,
                                "planted-token",
                                "a token",
                                Secret.fromString(PLANTED_SECRET))));
        String pipeline = """
                node() {
                    stage('ze-stage') {
                        withEnv(['KEY_%1$s=a-value', 'ZE_KEY=value-%1$s']) {
                            withCredentials([string(credentialsId: 'planted-token', variable: 'ZE_TOKEN')]) {
                                %2$s
                            }
                        }
                    }
                }
                """.formatted(PLANTED_SECRET, SHELL);

        runPipeline(pipeline, Result.SUCCESS);

        getBuildTrace();
        SpanData withEnv = onlySpan("withEnv");
        assertEquals(2L, withEnv.getAttributes().get(ENV_COUNT));
        List<String> envKeys = withEnv.getAttributes().asMap().keySet().stream()
                .map(AttributeKey::getKey)
                .filter(key -> key.contains(".env."))
                .collect(Collectors.toList());
        assertEquals(List.of("jenkins.pipeline.step.env.count"), envKeys);
        assertNoSecretInTrace(PLANTED_SECRET);
    }

    @Test
    public void testIgnoredStepsWinOverBlockSteps() throws Exception {
        configureBlockSteps("timeout,withEnv");
        JenkinsOpenTelemetryPluginConfiguration.get()
                .setIgnoredSteps(JenkinsOpenTelemetryPluginConfiguration.get().getIgnoredSteps() + ",timeout");
        String pipeline = """
                node() { stage('ze-stage') { timeout(time: 1, unit: 'MINUTES') { withEnv(['A=b']) { %s } } } }
                """.formatted(SHELL);

        runPipeline(pipeline, Result.SUCCESS);

        getBuildTrace();
        assertEquals(List.of("withEnv"), blockSpanNames());
    }

    @Test
    public void testStepsThePluginAlreadyHandlesAreNotBlockSpans() throws Exception {
        configureBlockSteps("node,stage,parallel,withNewSpan,setSpanAttributes,withSpanAttribute,withEnv");
        String pipeline = """
                node() {
                    stage('ze-stage') {
                        withNewSpan(label: 'ze-new-span') {
                            parallel a: { setSpanAttributes(key: 'k', value: 'v'); withEnv(['A=b']) { %s } }
                        }
                    }
                }
                """.formatted(SHELL);

        runPipeline(pipeline, Result.SUCCESS);

        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpansUpToRunPhase(
                spans,
                "ze-shell",
                "withEnv",
                "Parallel branch: a",
                "ze-new-span",
                "Stage: ze-stage",
                ExtendedJenkinsAttributes.AGENT_UI);
        List<String> names = exportedSpans().stream().map(SpanData::getName).collect(Collectors.toList());
        for (String unexpected : List.of("node", "stage", "parallel", "withNewSpan", "setSpanAttributes")) {
            assertFalse(names.contains(unexpected), "Unexpected span " + unexpected + " in " + names);
        }
        assertEquals(1, names.stream().filter("withEnv"::equals).count());
    }

    @Test
    public void testBlockSpanEndsAtTheTimeOfItsEndNode() throws Exception {
        configureBlockSteps("withEnv");
        String pipeline = """
                node() {
                    stage('ze-stage') {
                        parallel a: { withEnv(['A=b']) { sleep 1 } }, b: { sleep 4 }
                    }
                }
                """;

        WorkflowRun run = runPipeline(pipeline, Result.SUCCESS);

        getBuildTrace();
        SpanData withEnv = onlySpan("withEnv");
        long endNodeMillis = timeOfEndNodeOf(run, "withEnv");
        assertEquals(endNodeMillis, TimeUnit.NANOSECONDS.toMillis(withEnv.getEndEpochNanos()), 1.0);
        // The parallel branch ends when the join arrives, as before: the block span must not follow it
        SpanData branchA = onlySpan("Parallel branch: a");
        assertTrue(
                withEnv.getEndEpochNanos() < branchA.getEndEpochNanos() - TimeUnit.SECONDS.toNanos(1),
                "withEnv ended at " + withEnv.getEndEpochNanos() + ", branch at " + branchA.getEndEpochNanos());
    }

    @Test
    public void testBranchSpanIsTheSameWithBlockStepsAndWithout() throws Exception {
        String pipeline = """
                node() {
                    stage('ze-stage') {
                        parallel a: { withEnv(['A=b']) { sleep 1 } }, b: { sleep 3 }
                    }
                }
                """;
        runPipeline(pipeline, Result.SUCCESS);
        getBuildTrace();
        SpanData withoutBlockSteps = onlySpan("Parallel branch: a");
        long durationWithoutMillis = durationMillis(withoutBlockSteps);
        InMemorySpanExporterProvider.LAST_CREATED_INSTANCE.reset();

        configureBlockSteps("withEnv");
        runPipeline(pipeline, Result.SUCCESS);
        getBuildTrace();
        SpanData withBlockSteps = onlySpan("Parallel branch: a");

        assertEquals(durationWithoutMillis, durationMillis(withBlockSteps), 1000.0);
        assertTrue(durationMillis(onlySpan("withEnv")) < durationMillis(withBlockSteps));
    }

    @Test
    public void testFailingBodyEndsTheBlockSpanWithTheError() throws Exception {
        configureBlockSteps("withEnv,timeout");
        String pipeline = """
                node() { stage('ze-stage') { timeout(time: 1, unit: 'MINUTES') { withEnv(['A=b']) { error 'ze-boom' } } } }
                """;

        runPipeline(pipeline, Result.FAILURE);

        getBuildTrace();
        for (String name : List.of("withEnv", "timeout")) {
            SpanData span = onlySpan(name);
            assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode(), name);
            assertEquals("ze-boom", span.getStatus().getDescription(), name);
        }
    }

    /** A step that completes in `start()` without running its body: its end node follows its start node. */
    @Test
    public void testBlockStepWhoseBodyNeverStartsEndsAtTheOuterEndNode() throws Exception {
        configureBlockSteps("noBodyBlockTestStep");
        String pipeline = """
                node() { stage('ze-stage') { noBodyBlockTestStep { echo 'never' }; %s } }
                """.formatted(SHELL);

        WorkflowRun run = runPipeline(pipeline, Result.SUCCESS);

        Tree<SpanDataWrapper> spans = getBuildTrace();
        FlowNode endNode = endNodeOf(run, "noBodyBlockTestStep");
        assertNotNull(endNode, "the graph has an end node for the step");
        checkChainOfSpansUpToRunPhase(
                spans, "noBodyBlockTestStep", "Stage: ze-stage", ExtendedJenkinsAttributes.AGENT_UI);
        // The step after it is a child of the stage, not of the span that has ended
        checkChainOfSpansUpToRunPhase(spans, "ze-shell", "Stage: ze-stage", ExtendedJenkinsAttributes.AGENT_UI);
        SpanData noBody = onlySpan("noBodyBlockTestStep");
        assertEquals(StatusCode.OK, noBody.getStatus().getStatusCode());
        assertEquals(TimingAction.getStartTime(endNode), TimeUnit.NANOSECONDS.toMillis(noBody.getEndEpochNanos()), 1.0);
    }

    /** A step that fails in `start()` without running its body has an end node, right after its start node. */
    @Test
    public void testBlockStepWhoseBodyNeverStartsAndFailsEndsAtTheOuterEndNode() throws Exception {
        configureBlockSteps("noBodyBlockTestStep");
        String pipeline = """
                node() { stage('ze-stage') { noBodyBlockTestStep(fail: true) { echo 'never' } } }
                """;

        WorkflowRun run = runPipeline(pipeline, Result.FAILURE);

        Tree<SpanDataWrapper> spans = getBuildTrace();
        FlowNode endNode = endNodeOf(run, "noBodyBlockTestStep");
        assertNotNull(endNode, "the graph has an end node for the step");
        checkChainOfSpansUpToRunPhase(
                spans, "noBodyBlockTestStep", "Stage: ze-stage", ExtendedJenkinsAttributes.AGENT_UI);
        SpanData noBody = onlySpan("noBodyBlockTestStep");
        assertEquals(StatusCode.ERROR, noBody.getStatus().getStatusCode());
        assertEquals("ze-no-body", noBody.getStatus().getDescription());
        assertEquals(TimingAction.getStartTime(endNode), TimeUnit.NANOSECONDS.toMillis(noBody.getEndEpochNanos()), 1.0);
    }

    @Test
    public void testBlockStepAtTheTopLevelIsAChildOfTheRunPhase() throws Exception {
        configureBlockSteps("timeout");
        String pipeline = """
                timeout(time: 1, unit: 'MINUTES') {
                    node() { stage('ze-stage') { %s } }
                }
                """.formatted(SHELL);

        runPipeline(pipeline, Result.SUCCESS);

        Tree<SpanDataWrapper> spans = getBuildTrace();
        // No stage or agent around the block step: its parent is the span of the run phase
        checkChainOfSpansUpToRunPhase(spans, "timeout");
        checkChainOfSpansUpToRunPhase(
                spans, "ze-shell", "Stage: ze-stage", ExtendedJenkinsAttributes.AGENT_UI, "timeout");
        assertEquals(StatusCode.OK, onlySpan("timeout").getStatus().getStatusCode());
    }

    @Test
    public void testBlockStepInsideAParallelBranchIsAChildOfTheBranch() throws Exception {
        configureBlockSteps("withEnv");
        String pipeline = """
                node() {
                    stage('ze-stage') {
                        parallel a: { withEnv(['A=b']) { %s } }, b: { sleep 1 }
                    }
                }
                """.formatted(SHELL);

        runPipeline(pipeline, Result.SUCCESS);

        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpansUpToRunPhase(
                spans,
                "ze-shell",
                "withEnv",
                "Parallel branch: a",
                "Stage: ze-stage",
                ExtendedJenkinsAttributes.AGENT_UI);
        assertEquals(1, blockSpanNames().size(), blockSpanNames().toString());
        assertEquals(StatusCode.OK, onlySpan("withEnv").getStatus().getStatusCode());
    }

    @Test
    public void testUserAbortEndsTheBlockSpansAndTheyAreNotOk() throws Exception {
        configureBlockSteps("timeout,withEnv");
        String pipeline = """
                node() {
                    stage('ze-stage') {
                        timeout(time: 5, unit: 'MINUTES') {
                            withEnv(['A=b']) { echo 'ze-waiting'; sleep 120 }
                        }
                    }
                }
                """;

        WorkflowRun run = startPipeline(pipeline);
        jenkinsRule.waitForMessage("ze-waiting", run);
        Thread.sleep(2000); // the sleep step has started
        run.getOneOffExecutor().interrupt(Result.ABORTED);
        jenkinsRule.assertBuildStatus(Result.ABORTED, jenkinsRule.waitForCompletion(run));

        getBuildTrace();
        SpanData stage = onlySpan("Stage: ze-stage");
        for (String name : List.of("timeout", "withEnv")) {
            SpanData span = onlySpan(name);
            assertNotEquals(StatusCode.OK, span.getStatus().getStatusCode(), name);
            assertTrue(
                    TimeUnit.NANOSECONDS.toMillis(span.getEndEpochNanos())
                            <= TimeUnit.NANOSECONDS.toMillis(stage.getEndEpochNanos()),
                    name + " ended after its stage");
        }
        // The timeout did not fire: the user did
        assertEquals(Boolean.FALSE, onlySpan("timeout").getAttributes().get(TIMEOUT_EXCEEDED));
    }

    @Test
    public void testBlockSpanLostLikeAfterARestartDoesNotAffectTheBuild() throws Exception {
        configureBlockSteps("withEnv");
        String pipeline = """
                node() { stage('ze-stage') { withEnv(['A=b']) { forgetBlockSpansTestStep(); %s } } }
                """.formatted(SHELL);

        runPipeline(pipeline, Result.SUCCESS);

        Tree<SpanDataWrapper> spans = getBuildTrace();
        // The span of the block is gone from the graph, as after a controller restart: it is never exported and
        // the spans around it are not touched.
        assertEquals(List.of(), blockSpanNames());
        checkChainOfSpansUpToRunPhase(spans, "ze-shell", "Stage: ze-stage", ExtendedJenkinsAttributes.AGENT_UI);
        for (SpanData span : exportedSpans()) {
            assertNotNull(span.getName());
        }
        assertEquals(DEFAULT_SPAN_COUNT + 1, exportedSpans().size()); // + the span of the step
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    /** Spans of a build with a stage, an agent and one sh, before block steps existed. Set from the first run. */
    private static final int DEFAULT_SPAN_COUNT = 8;

    private void configureBlockSteps(String blockSteps) {
        JenkinsOpenTelemetryPluginConfiguration.get().setBlockSteps(blockSteps);
    }

    private WorkflowRun runPipeline(String script, Result expected) throws Exception {
        return jenkinsRule.assertBuildStatus(expected, jenkinsRule.waitForCompletion(startPipeline(script)));
    }

    /** @return the run after it has started, without waiting for it to complete */
    private WorkflowRun startPipeline(String script) throws Exception {
        jenkinsRule.createOnlineSlave();
        WorkflowJob job =
                jenkinsRule.createProject(WorkflowJob.class, "test-block-steps-" + jobNameSuffix.incrementAndGet());
        job.setDefinition(new CpsFlowDefinition(script, true));
        return job.scheduleBuild2(0).waitForStart();
    }

    private List<SpanData> exportedSpans() {
        return InMemorySpanExporterProvider.LAST_CREATED_INSTANCE.getFinishedSpanItems();
    }

    private List<String> blockSpanNames() {
        return exportedSpans().stream()
                .map(SpanData::getName)
                .filter(BLOCK_STEP_SPAN_NAMES::contains)
                .sorted()
                .collect(Collectors.toList());
    }

    private SpanData onlySpan(String name) {
        List<SpanData> found =
                exportedSpans().stream().filter(s -> name.equals(s.getName())).collect(Collectors.toList());
        assertEquals(1, found.size(), "spans named '" + name + "'");
        return found.get(0);
    }

    private static long durationMillis(SpanData span) {
        return TimeUnit.NANOSECONDS.toMillis(span.getEndEpochNanos() - span.getStartEpochNanos());
    }

    private static long timeOfEndNodeOf(WorkflowRun run, String functionName) throws Exception {
        FlowNode end = endNodeOf(run, functionName);
        if (end == null) {
            throw new AssertionError("No end node of " + functionName);
        }
        return TimingAction.getStartTime(end);
    }

    /** @return the end node of the step (not of its body), or {@code null} if the graph has none */
    private static FlowNode endNodeOf(WorkflowRun run, String functionName) throws Exception {
        FlowExecution execution = run.getExecution();
        for (FlowNode node : new org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner()
                .allNodes(execution.getCurrentHeads())) {
            if (node instanceof StepEndNode end
                    && end.getStartNode().getDescriptor() != null
                    && functionName.equals(end.getStartNode().getDescriptor().getFunctionName())
                    && end.getAction(org.jenkinsci.plugins.workflow.actions.BodyInvocationAction.class) == null) {
                return end;
            }
        }
        return null;
    }

    /** A block step that never runs its body: it completes in {@code start()}, or fails if {@code fail} is set. */
    public static class NoBodyBlockTestStep extends Step {
        private boolean fail;

        @DataBoundConstructor
        public NoBodyBlockTestStep() {}

        @DataBoundSetter
        public void setFail(boolean fail) {
            this.fail = fail;
        }

        public boolean isFail() {
            return fail;
        }

        @Override
        public StepExecution start(StepContext context) {
            return new StepExecution(context) {
                @Serial
                private static final long serialVersionUID = 1L;

                @Override
                public boolean start() {
                    if (isFail()) {
                        getContext().onFailure(new AbortException("ze-no-body"));
                    } else {
                        getContext().onSuccess(null);
                    }
                    return true;
                }
            };
        }

        @TestExtension
        public static class DescriptorImpl extends StepDescriptor {
            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Set.of();
            }

            @Override
            public String getFunctionName() {
                return "noBodyBlockTestStep";
            }

            @Override
            public boolean takesImplicitBlockArgument() {
                return true;
            }

            @Override
            public String getDisplayName() {
                return "noBodyBlockTestStep";
            }
        }
    }

    /** Drops the spans that the plugin keeps for the enclosing blocks, as a controller restart does. */
    public static class ForgetBlockSpansTestStep extends Step {
        @DataBoundConstructor
        public ForgetBlockSpansTestStep() {}

        @Override
        public StepExecution start(StepContext context) {
            return new SynchronousStepExecution<Void>(context) {
                @Serial
                private static final long serialVersionUID = 1L;

                @Override
                protected Void run() throws Exception {
                    FlowNode node = getContext().get(FlowNode.class);
                    for (FlowNode block : node.getEnclosingBlocks()) {
                        block.getActions(FlowNodeMonitoringAction.class)
                                .forEach(FlowNodeMonitoringAction::purgeSpanAndCloseAssociatedScopes);
                    }
                    return null;
                }
            };
        }

        @TestExtension
        public static class DescriptorImpl extends StepDescriptor {
            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Set.of(FlowNode.class);
            }

            @Override
            public String getFunctionName() {
                return "forgetBlockSpansTestStep";
            }

            @Override
            public String getDisplayName() {
                return "forgetBlockSpansTestStep";
            }
        }
    }
}
