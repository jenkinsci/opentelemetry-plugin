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

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.rutledgepaulv.prune.Tree;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporterProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.LogRecorder;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * With "Record credential use as span events" on, each use of a credential by a build is an event
 * {@code jenkins.credentials.use} on a span of the build's trace.
 */
public class JenkinsOtelPluginCredentialsUseEventsIntegrationTest extends BaseIntegrationTest {

    private static final String EVENT_NAME = "jenkins.credentials.use";
    private static final AttributeKey<String> TYPE = AttributeKey.stringKey("jenkins.credentials.type");
    private static final AttributeKey<String> CLASS = AttributeKey.stringKey("jenkins.credentials.class");
    private static final AttributeKey<String> SCOPE = AttributeKey.stringKey("jenkins.credentials.scope");
    private static final AttributeKey<String> ID = AttributeKey.stringKey("jenkins.credentials.id");

    private static final String PASSWORD = "planted-password-5e1b8c02";
    private static final String USER_NAME = "planted-user-name-9d47a3f6";
    private static final String DESCRIPTION = "planted-description-2c80ef19";
    private static final String USER_VARIABLE = "PLANTED_USER_VARIABLE_41b7";
    private static final String PASSWORD_VARIABLE = "PLANTED_PASSWORD_VARIABLE_7e92";
    private static final String ENVIRONMENT_VALUE = "planted-environment-value-6a3d05c8";
    private static final String PLANTED_ID = "planted-credential-id-3b9f1d70";
    private static final String ARGUMENT = "planted-argument-b04f7e21";

    private record Event(SpanData span, EventData event) {}

    private void addCredentials(String... ids) throws Exception {
        List<Credentials> credentials = new ArrayList<>();
        for (String id : ids) {
            credentials.add(
                    new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, id, DESCRIPTION, USER_NAME, PASSWORD));
        }
        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(), credentials);
    }

    private void configure(boolean recordEvents, boolean includeIds) {
        JenkinsOpenTelemetryPluginConfiguration configuration = JenkinsOpenTelemetryPluginConfiguration.get();
        configuration.setRecordCredentialUseEvents(recordEvents);
        configuration.setIncludeCredentialIds(includeIds);
    }

    private List<Event> credentialEvents() {
        jenkinsControllerOpenTelemetry
                .getOpenTelemetrySdk()
                .getSdkTracerProvider()
                .forceFlush()
                .join(1, TimeUnit.SECONDS);
        List<Event> result = new ArrayList<>();
        for (SpanData span : InMemorySpanExporterProvider.LAST_CREATED_INSTANCE.getFinishedSpanItems()) {
            for (EventData event : span.getEvents()) {
                if (EVENT_NAME.equals(event.getName())) {
                    result.add(new Event(span, event));
                }
            }
        }
        return result;
    }

    private WorkflowRun runPipeline(String script) throws Exception {
        WorkflowJob pipeline =
                jenkinsRule.createProject(WorkflowJob.class, "credentials-use-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(script, true));
        return jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
    }

    private static String withUsernamePassword(String credentialsId, String body) {
        return "withCredentials([usernamePassword(credentialsId: '" + credentialsId + "', usernameVariable: '"
                + USER_VARIABLE + "', passwordVariable: '" + PASSWORD_VARIABLE + "')]) {\n" + body + "\n}";
    }

    private static String withEnv(String body) {
        return "withEnv(['ZE_ENVIRONMENT=" + ENVIRONMENT_VALUE + "']) {\n" + body + "\n}";
    }

    private static final String WITH_CREDENTIALS_PIPELINE =
            "stage('ze-stage') {\n" + withUsernamePassword("ze-cred", "echo 'inside'") + "\n}";

    @Test
    public void testEventOnASpanOfTheBuildThatUsedTheCredential() throws Exception {
        addCredentials("ze-cred");
        configure(true, false);

        WorkflowRun run = runPipeline(WITH_CREDENTIALS_PIPELINE);

        List<Event> events = credentialEvents();
        assertEquals(1, events.size(), events::toString);
        Event event = events.get(0);
        assertEquals("Username with password", event.event().getAttributes().get(TYPE));
        assertEquals("GLOBAL", event.event().getAttributes().get(SCOPE));
        assertEquals(
                UsernamePasswordCredentialsImpl.class.getName(),
                event.event().getAttributes().get(CLASS));
        assertNull(event.event().getAttributes().get(ID));
        assertEquals(
                run.getAction(MonitoringAction.class).getTraceId(), event.span().getTraceId());
        // withCredentials binds on a pool thread. Without the context fixes of issue 1312 that thread has no current
        // span of the build, so the event goes to the Phase: Run span; with them it goes to the span of the stage.
        assertTrue(
                Set.of("Phase: Run", "Stage: ze-stage").contains(event.span().getName()),
                event.span().getName());
    }

    @Test
    public void testCredentialIdIsInTheEventOnlyWhenTheOptionIsOn() throws Exception {
        addCredentials("ze-cred");
        configure(true, true);

        runPipeline(WITH_CREDENTIALS_PIPELINE);

        List<Event> events = credentialEvents();
        assertEquals(1, events.size(), events::toString);
        assertEquals("ze-cred", events.get(0).event().getAttributes().get(ID));
    }

    @Test
    public void testNoEventAndTheSameSpansWhenTheSettingIsOff() throws Exception {
        addCredentials("ze-cred");
        configure(false, true);

        runPipeline(WITH_CREDENTIALS_PIPELINE);

        assertEquals(0, credentialEvents().size());
        Tree<SpanDataWrapper> spansOff = getBuildTrace();
        checkChainOfSpansUpToRunPhase(spansOff, "Stage: ze-stage");
        long cardinalityOff = spansOff.cardinality();
        List<String> namesOff = spanNames(spansOff);

        configure(true, true);
        runPipeline(WITH_CREDENTIALS_PIPELINE);

        assertEquals(1, credentialEvents().size());
        Tree<SpanDataWrapper> spansOn = getBuildTrace(1);
        assertEquals(cardinalityOff, spansOn.cardinality());
        assertEquals(namesOff, spanNames(spansOn));
    }

    private static List<String> spanNames(Tree<SpanDataWrapper> spans) {
        return spans.breadthFirstStreamNodes()
                .map(node -> node.getData().spanData.getName())
                .map(name -> name.startsWith("BUILD ") ? "BUILD" : name)
                .sorted()
                .collect(Collectors.toList());
    }

    @Test
    public void testNoSecretUserNameDescriptionOrVariableNameInTheTrace() throws Exception {
        addCredentials("ze-cred");
        configure(true, true);

        runPipeline("stage('ze-stage') {\n" + "withEnv(['ZE_ENVIRONMENT=" + ENVIRONMENT_VALUE + "']) {\n"
                + withUsernamePassword("ze-cred", "echo 'argument " + ARGUMENT + "'") + "\n}\n}");

        assertEquals(1, credentialEvents().size());
        assertNoSecretInTrace(
                PASSWORD, USER_NAME, DESCRIPTION, USER_VARIABLE, PASSWORD_VARIABLE, ENVIRONMENT_VALUE, ARGUMENT);
    }

    @Test
    public void testNoCredentialIdAnywhereInTheTraceWhenTheOptionIsOff() throws Exception {
        addCredentials(PLANTED_ID);
        configure(true, false);

        runPipeline("stage('ze-stage') {\n"
                + withEnv(withUsernamePassword(PLANTED_ID, "echo 'argument " + ARGUMENT + "'")) + "\n}");

        assertEquals(1, credentialEvents().size());
        // The ID is in no span name, attribute key or value, status or event, with the option off
        assertNoSecretInTrace(PLANTED_ID, PASSWORD, USER_NAME, DESCRIPTION, USER_VARIABLE, PASSWORD_VARIABLE);
    }

    @Test
    public void testCredentialIdIsOnlyInTheEventWhenTheOptionIsOnAndInNoOtherSpanAttribute() throws Exception {
        addCredentials(PLANTED_ID);
        configure(true, true);

        runPipeline("stage('ze-stage') {\n" + withUsernamePassword(PLANTED_ID, "echo 'inside'") + "\n}");

        List<Event> events = credentialEvents();
        assertEquals(1, events.size());
        assertEquals(PLANTED_ID, events.get(0).event().getAttributes().get(ID));
        for (SpanData span : InMemorySpanExporterProvider.LAST_CREATED_INSTANCE.getFinishedSpanItems()) {
            assertFalse(span.getName().contains(PLANTED_ID), span.getName());
            span.getAttributes()
                    .forEach((key, value) -> assertFalse(
                            String.valueOf(value).contains(PLANTED_ID),
                            "attribute " + key.getKey() + " of " + span.getName()));
        }
    }

    @Test
    public void testEventOfAGitStepWithACredential() throws Exception {
        addCredentials("ze-cred");
        configure(true, false);
        sampleRepo.init();
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "file");
        sampleRepo.git("commit", "--all", "--message=init");

        WorkflowRun run = runPipeline("stage('ze-stage') {\n  node() {\n    git credentialsId: 'ze-cred', url: $/"
                + sampleRepo + "/$\n  }\n}");

        List<Event> events = credentialEvents();
        assertEquals(1, events.size(), events::toString);
        assertEquals(
                run.getAction(MonitoringAction.class).getTraceId(),
                events.get(0).span().getTraceId());
        assertTrue(
                events.get(0).span().getName().startsWith("git"),
                events.get(0).span().getName());
        // The git step span already has the user name of its credential (attribute git.username, before this change)
        assertNoSecretInTrace(PASSWORD, DESCRIPTION);
        assertFalse(events.get(0).event().getAttributes().toString().contains(USER_NAME));
    }

    @Test
    public void testEventOfAJenkinsfileCheckoutBeforeTheStepsOfThePipeline() throws Exception {
        addCredentials("ze-cred");
        configure(true, true);
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo 'from the Jenkinsfile'");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowJob pipeline =
                jenkinsRule.createProject(WorkflowJob.class, "credentials-use-scm-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsScmFlowDefinition(
                new GitSCM(
                        GitSCM.createRepoList(sampleRepo.toString(), "ze-cred"),
                        List.of(new BranchSpec("*/master")),
                        null,
                        null,
                        List.<GitSCMExtension>of()),
                "Jenkinsfile"));

        WorkflowRun run = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        List<Event> events = credentialEvents();
        assertEquals(1, events.size(), events::toString);
        assertEquals(
                run.getAction(MonitoringAction.class).getTraceId(),
                events.get(0).span().getTraceId());
        assertEquals("Phase: Run", events.get(0).span().getName());
    }

    @Test
    public void testTheSameCredentialUsedTwiceInOneSpanGivesOneEvent() throws Exception {
        addCredentials("ze-cred");
        configure(true, false);

        runPipeline("stage('ze-stage') {\n" + withUsernamePassword("ze-cred", "echo 'first'") + "\n"
                + withUsernamePassword("ze-cred", "echo 'second'") + "\n}");

        assertEquals(1, credentialEvents().size());
    }

    @Test
    public void testNumberOfEventsPerSpanIsCapped() throws Exception {
        int cap = 32;
        int used = cap + 8;
        String[] ids = IntStream.range(0, used).mapToObj(i -> "cred-" + i).toArray(String[]::new);
        addCredentials(ids);
        configure(true, true);
        String usernamePasswordBindings = IntStream.range(0, used)
                .mapToObj(i -> "usernamePassword(credentialsId: 'cred-" + i + "', usernameVariable: 'U" + i
                        + "', passwordVariable: 'P" + i + "')")
                .collect(Collectors.joining(", "));

        runPipeline("stage('ze-stage') {\nwithCredentials([" + usernamePasswordBindings + "]) {\necho 'inside'\n}\n}");

        assertEquals(cap, credentialEvents().size());
    }

    @Test
    public void testBuildsInParallelRecordTheirEventsInTheirOwnTrace() throws Exception {
        addCredentials("cred-a", "cred-b");
        configure(true, true);
        WorkflowJob jobA = jenkinsRule.createProject(WorkflowJob.class, "credentials-use-a");
        jobA.setDefinition(new CpsFlowDefinition(
                "stage('stage-a') {\ncredentialEventsHoldStep 'build-a'\n" + withUsernamePassword("cred-a", "echo 'a'")
                        + "\n}",
                true));
        WorkflowJob jobB = jenkinsRule.createProject(WorkflowJob.class, "credentials-use-b");
        jobB.setDefinition(new CpsFlowDefinition(
                "stage('stage-b') {\ncredentialEventsHoldStep 'build-b'\n" + withUsernamePassword("cred-b", "echo 'b'")
                        + "\n}",
                true));

        QueueTaskFuture<WorkflowRun> buildA = jobA.scheduleBuild2(0);
        QueueTaskFuture<WorkflowRun> buildB = jobB.scheduleBuild2(0);
        // Both builds hold their step at once, so that their credential uses overlap. A build that started is
        // released even if the other never starts, else it blocks the teardown of the Jenkins rule.
        List<String> started = new ArrayList<>();
        try {
            HoldStep.waitForStart("build-a");
            started.add("build-a");
            HoldStep.waitForStart("build-b");
            started.add("build-b");
        } finally {
            Collections.reverse(started);
            started.forEach(HoldStep::release);
        }
        WorkflowRun runA = jenkinsRule.assertBuildStatusSuccess(buildA);
        WorkflowRun runB = jenkinsRule.assertBuildStatusSuccess(buildB);

        List<Event> events = credentialEvents();
        assertEquals(2, events.size(), events::toString);
        for (Event event : events) {
            String credentialId = event.event().getAttributes().get(ID);
            WorkflowRun owner = "cred-a".equals(credentialId) ? runA : runB;
            WorkflowRun other = owner == runA ? runB : runA;
            assertEquals(
                    owner.getAction(MonitoringAction.class).getTraceId(),
                    event.span().getTraceId(),
                    credentialId);
            assertNotEquals(
                    other.getAction(MonitoringAction.class).getTraceId(),
                    event.span().getTraceId(),
                    credentialId);
        }
    }

    @Test
    public void testUsesByANodeOrAnItemAreNotRecorded() throws Exception {
        addCredentials("ze-cred");
        configure(true, true);
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "credentials-use-blocked");
        pipeline.setDefinition(new CpsFlowDefinition("credentialEventsHoldStep 'blocked'", true));
        QueueTaskFuture<WorkflowRun> build = pipeline.scheduleBuild2(0);
        HoldStep.waitForStart("blocked");
        try {
            Credentials credentials = SystemCredentialsProvider.getInstance()
                    .getDomainCredentialsMap()
                    .get(Domain.global())
                    .get(0);
            CredentialsProvider.track(jenkinsRule.jenkins, credentials);
            CredentialsProvider.track(pipeline, credentials);
        } finally {
            HoldStep.release("blocked");
        }
        jenkinsRule.assertBuildStatusSuccess(build);
        assertEquals(0, credentialEvents().size());
    }

    @Test
    public void testAFailureInTheListenerIsLoggedAtFineAndNotAsAWarning() throws Exception {
        addCredentials("ze-cred");
        configure(true, true);
        LogRecorder coreListeners = new LogRecorder().record("jenkins.util.Listeners", Level.WARNING);
        coreListeners.capture(50);
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "credentials-use-failure");
        pipeline.setDefinition(new CpsFlowDefinition("credentialEventsHoldStep 'failing'", true));
        QueueTaskFuture<WorkflowRun> build = pipeline.scheduleBuild2(0);
        HoldStep.waitForStart("failing");
        WorkflowRun run = pipeline.getLastBuild();
        UsernamePasswordCredentialsImpl broken = new BrokenCredentials();

        try {
            CredentialsProvider.track(run, broken);
        } finally {
            // Always complete the blocked build, else the test never ends
            HoldStep.release("failing");
        }
        jenkinsRule.assertBuildStatusSuccess(build);
        assertEquals(0, credentialEvents().size());
        assertFalse(
                coreListeners.getMessages().stream().anyMatch(message -> message.contains("forced failure")),
                coreListeners.getMessages()::toString);
        assertEquals(List.of(), coreListeners.getRecords());
    }

    /**
     * Test step that holds its build until the test calls {@link #release(String)}, so that a test can use a
     * credential while the build runs.
     */
    public static class HoldStep extends Step {
        private static final Map<String, StepContext> STARTED = new ConcurrentHashMap<>();

        private final String id;

        @DataBoundConstructor
        public HoldStep(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public StepExecution start(StepContext context) {
            return new Execution(context, id);
        }

        /** Waits until a build has started the step with this id. */
        public static void waitForStart(String id) throws InterruptedException {
            for (int i = 0; i < 600 && !STARTED.containsKey(id); i++) {
                Thread.sleep(100);
            }
            if (!STARTED.containsKey(id)) {
                throw new AssertionError("No build started credentialEventsHoldStep '" + id + "' in 60 s");
            }
        }

        /** Completes the step with this id: its build goes on. */
        public static void release(String id) {
            Objects.requireNonNull(STARTED.remove(id), id).onSuccess(null);
        }

        private static class Execution extends StepExecution {
            @Serial
            private static final long serialVersionUID = 1L;

            private final String id;

            Execution(StepContext context, String id) {
                super(context);
                this.id = id;
            }

            @Override
            public boolean start() {
                STARTED.put(id, getContext());
                return false;
            }

            @Override
            public void stop(Throwable cause) {
                STARTED.remove(id);
                getContext().onFailure(cause);
            }
        }

        @Extension
        public static class DescriptorImpl extends StepDescriptor {
            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Set.of();
            }

            @Override
            public String getFunctionName() {
                return "credentialEventsHoldStep";
            }

            @Override
            public String getDisplayName() {
                return "credentialEventsHoldStep";
            }
        }
    }

    /** Credentials that fail when the listener reads their scope. */
    private static class BrokenCredentials extends UsernamePasswordCredentialsImpl {
        private static final long serialVersionUID = 1L;

        BrokenCredentials() throws Descriptor.FormException {
            super(CredentialsScope.GLOBAL, "broken", DESCRIPTION, USER_NAME, PASSWORD);
        }

        @Override
        public CredentialsScope getScope() {
            throw new IllegalStateException("forced failure");
        }
    }
}
