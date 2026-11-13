/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.rutledgepaulv.prune.Tree;
import hudson.model.Result;
import hudson.util.Secret;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;

/**
 * A {@code withCredentials} listed in the block steps setting gets a span with the kinds of its bindings, and the
 * credential IDs only when the setting for IDs is on. Variable names, user names and secrets are never recorded.
 */
public class JenkinsOtelPluginWithCredentialsIntegrationTest extends BaseIntegrationTest {

    private static final String SECRET = "planted-secret-5c1f8a30";
    private static final String USER = "planted-user-7e44b9d2";
    private static final String PASSWORD = "planted-password-1b6d03ac";
    private static final String VARIABLE = "PLANTED_VARIABLE_3f9a";
    private static final String ENV_VALUE = "planted-env-value-8d27e5f1";
    private static final AttributeKey<List<String>> IDS =
            AttributeKey.stringArrayKey("jenkins.pipeline.step.credentials.ids");
    private static final AttributeKey<List<String>> BINDING_TYPES =
            AttributeKey.stringArrayKey("jenkins.pipeline.step.credentials.binding_types");
    private static final String SHELL = "if (isUnix()) { sh label: 'ze-shell', script: 'echo ze-echo' } "
            + "else { bat label: 'ze-shell', script: 'echo ze-echo' }";
    /** Spans of a build with a stage, an agent and one sh. */
    private static final int DEFAULT_SPAN_COUNT = 8;

    private String jobName;

    @Test
    public void testNothingChangesWhenWithCredentialsIsNotListed() throws Exception {
        plantCredentials();
        configure("timeout", true);

        runPipeline(stringPipeline(), Result.SUCCESS);

        Tree<SpanDataWrapper> spans = getBuildTrace();
        // The whole tree of the build before block steps existed: each span name with the names of its children
        Map<String, List<String>> expectedTree = new TreeMap<>();
        expectedTree.put(
                ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName,
                List.of("Phase: Finalise", "Phase: Run", "Phase: Start"));
        expectedTree.put("Phase: Start", List.of());
        expectedTree.put("Phase: Run", List.of("Agent"));
        expectedTree.put("Agent", List.of("Agent Allocation", "Stage: ze-stage"));
        expectedTree.put("Agent Allocation", List.of());
        expectedTree.put("Stage: ze-stage", List.of("ze-shell"));
        expectedTree.put("ze-shell", List.of());
        expectedTree.put("Phase: Finalise", List.of());
        Map<String, List<String>> actualTree = new TreeMap<>();
        getSpanMapWithChildrenFromTree(spans)
                .forEach((name, children) ->
                        actualTree.put(name, children.stream().sorted().toList()));
        assertEquals(expectedTree, actualTree);
    }

    @Test
    public void testSpanHasTheBindingTypesAndNoIdsByDefault() throws Exception {
        plantCredentials();
        configure("withCredentials", false);

        runPipeline(stringPipeline(), Result.SUCCESS);

        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpansUpToRunPhase(
                spans, "ze-shell", "withCredentials", "Stage: ze-stage", ExtendedJenkinsAttributes.AGENT_UI);
        SpanData span = onlySpan("withCredentials");
        assertEquals(List.of("string"), span.getAttributes().get(BINDING_TYPES));
        assertNull(span.getAttributes().get(IDS));
        assertEquals(DEFAULT_SPAN_COUNT + 1, exportedSpans().size());
    }

    @Test
    public void testSpanHasTheCredentialIdsWhenTheSettingIsOn() throws Exception {
        plantCredentials();
        configure("withCredentials", true);
        String pipeline = """
                node() {
                    stage('ze-stage') {
                        withCredentials([string(credentialsId: 'planted-token', variable: 'T'),
                                         usernamePassword(credentialsId: 'planted-login', usernameVariable: 'U', passwordVariable: 'P')]) {
                            %s
                        }
                    }
                }
                """.formatted(SHELL);

        runPipeline(pipeline, Result.SUCCESS);

        getBuildTrace();
        SpanData span = onlySpan("withCredentials");
        assertEquals(
                Set.of("string", "usernamePassword"),
                Set.copyOf(span.getAttributes().get(BINDING_TYPES)));
        assertEquals(
                Set.of("planted-token", "planted-login"),
                Set.copyOf(span.getAttributes().get(IDS)));
    }

    @Test
    public void testNoSecretVariableNameOrUserNameIsRecorded() throws Exception {
        plantCredentials();
        configure("withCredentials,withEnv", false);
        String pipeline = """
                node() {
                    stage('ze-stage') {
                        withEnv(['ZE_ENV=%1$s']) {
                            withCredentials([string(credentialsId: 'planted-token', variable: '%2$s'),
                                             usernamePassword(credentialsId: 'planted-login', usernameVariable: 'USER_%2$s', passwordVariable: 'PASS_%2$s')]) {
                                %3$s
                            }
                        }
                    }
                }
                """.formatted(ENV_VALUE, VARIABLE, SHELL);

        runPipeline(pipeline, Result.SUCCESS);

        getBuildTrace();
        assertNotNull(onlySpan("withCredentials"));
        assertNoSecretInTrace(SECRET, USER, PASSWORD, VARIABLE, ENV_VALUE, "planted-token", "planted-login");
    }

    @Test
    public void testMissingCredentialEndsTheSpanWithAnErrorAtTheEndNodeTime() throws Exception {
        configure("withCredentials", true);
        String pipeline = """
                node() {
                    stage('ze-stage') {
                        withCredentials([string(credentialsId: 'no-such-credential', variable: 'T')]) {
                            %s
                        }
                    }
                }
                """.formatted(SHELL);

        WorkflowRun run = runPipeline(pipeline, Result.FAILURE);

        getBuildTrace();
        SpanData span = onlySpan("withCredentials");
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        long endNodeTime = timeOfEndNodeOf(run, "withCredentials");
        long spanEnd = span.getEndEpochNanos() / 1_000_000;
        assertTrue(Math.abs(spanEnd - endNodeTime) <= 1, "span end " + spanEnd + " end node " + endNodeTime);
    }

    @Test
    public void testMissingCredentialIdIsNotInTheSpanWhenIdsAreOff() throws Exception {
        configure("withCredentials", false);
        String pipeline = """
                node() {
                    stage('ze-stage') {
                        withCredentials([string(credentialsId: 'planted-missing-id', variable: 'T')]) {
                            %s
                        }
                    }
                }
                """.formatted(SHELL);

        runPipeline(pipeline, Result.FAILURE);

        getBuildTrace();
        SpanData span = onlySpan("withCredentials");
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertTrue(span.getEvents().stream().anyMatch(e -> "exception".equals(e.getName())));
        List<String> texts = new java.util.ArrayList<>();
        texts.add(span.getName());
        texts.add(span.getStatus().getDescription());
        span.getAttributes().forEach((key, value) -> texts.add(key.getKey() + "=" + value));
        span.getEvents().forEach(event -> {
            texts.add(event.getName());
            event.getAttributes().forEach((key, value) -> texts.add(key.getKey() + "=" + value));
        });
        texts.forEach(text -> assertFalse(text.contains("planted-missing-id"), "ID in '" + text + "'"));
        // Pre-existing behavior of the plugin, not changed here: the stage span carries the message of the error
        assertTrue(onlySpan("Stage: ze-stage").getStatus().getDescription().contains("planted-missing-id"));
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    private void configure(String blockSteps, boolean includeIds) {
        JenkinsOpenTelemetryPluginConfiguration configuration = JenkinsOpenTelemetryPluginConfiguration.get();
        configuration.setBlockSteps(blockSteps);
        configuration.setIncludeCredentialIds(includeIds);
    }

    private void plantCredentials() throws Exception {
        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(
                        Domain.global(),
                        List.of(
                                new StringCredentialsImpl(
                                        CredentialsScope.GLOBAL, "planted-token", "a token", Secret.fromString(SECRET)),
                                new UsernamePasswordCredentialsImpl(
                                        CredentialsScope.GLOBAL, "planted-login", "a login", USER, PASSWORD)));
    }

    private String stringPipeline() {
        return """
                node() {
                    stage('ze-stage') {
                        withCredentials([string(credentialsId: 'planted-token', variable: 'T')]) {
                            %s
                        }
                    }
                }
                """.formatted(SHELL);
    }

    private WorkflowRun runPipeline(String script, Result expected) throws Exception {
        jenkinsRule.createOnlineSlave();
        jobName = "test-with-credentials-" + jobNameSuffix.incrementAndGet();
        WorkflowJob job = jenkinsRule.createProject(WorkflowJob.class, jobName);
        job.setDefinition(new CpsFlowDefinition(script, true));
        return jenkinsRule.assertBuildStatus(expected, job.scheduleBuild2(0));
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

    private static long timeOfEndNodeOf(WorkflowRun run, String functionName) throws Exception {
        for (FlowNode node : new org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner()
                .allNodes(run.getExecution().getCurrentHeads())) {
            if (node instanceof StepEndNode end
                    && end.getStartNode().getDescriptor() != null
                    && functionName.equals(end.getStartNode().getDescriptor().getFunctionName())
                    && end.getAction(org.jenkinsci.plugins.workflow.actions.BodyInvocationAction.class) == null) {
                return TimingAction.getStartTime(end);
            }
        }
        throw new AssertionError("No end node of " + functionName);
    }
}
