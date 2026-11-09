/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.jenkins.plugins.opentelemetry.job.step.ContextLeakBlockTestStep;
import io.jenkins.plugins.opentelemetry.job.step.ContextLeakProbeTestStep;
import io.jenkins.plugins.opentelemetry.job.step.ContextLeakProbeTestStep.Observation;
import io.jenkins.plugins.opentelemetry.job.step.ContextLeakProgramProbe;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.TestExtension;

/**
 * The CPS VM runs the pipelines of all builds on one pool of threads. A step span that the plugin makes current on a
 * pool thread must not stay current there for the next build that runs on that thread.
 */
public class JenkinsOtelPluginCpsVmContextIntegrationTest extends BaseIntegrationTest {

    private static final int PROBE_BUILDS = 3;
    private static final int PROBES_PER_BUILD = 3;

    /**
     * A block step without a span of its own starts in the context of its pool thread. That context must be one of
     * the step's build: here other builds run while one build is blocked in its first CPS VM task, then in a later
     * one, each time with the span of its blocking step current when its thread went back to the pool.
     */
    @Test
    public void testBlockStepStartsInTheContextOfItsBuild() throws Exception {
        ContextLeakProbeTestStep.OBSERVATIONS.clear();
        WorkflowJob blocked = pipeline("blocked", """
                contextLeakBlockTestStep 'first-task'
                sleep time: 10, unit: 'MILLISECONDS'
                contextLeakBlockTestStep 'later-task'
                """);
        WorkflowJob probe = pipeline("probe", """
                contextLeakProbeTestStep('1') {}
                sleep time: 10, unit: 'MILLISECONDS'
                contextLeakProbeTestStep('2') {}
                sleep time: 10, unit: 'MILLISECONDS'
                contextLeakProbeTestStep('3') {}
                """);
        List<WorkflowRun> runs = new ArrayList<>();

        QueueTaskFuture<WorkflowRun> blockedBuild = blocked.scheduleBuild2(0);
        ContextLeakBlockTestStep.waitForStart("first-task");
        for (int i = 0; i < PROBE_BUILDS; i++) {
            runs.add(jenkinsRule.buildAndAssertSuccess(probe));
        }
        ContextLeakBlockTestStep.release("first-task");
        ContextLeakBlockTestStep.waitForStart("later-task");
        for (int i = 0; i < PROBE_BUILDS; i++) {
            runs.add(jenkinsRule.buildAndAssertSuccess(probe));
        }
        ContextLeakBlockTestStep.release("later-task");
        runs.add(jenkinsRule.assertBuildStatusSuccess(blockedBuild));

        Map<String, String> buildOfTrace = runs.stream()
                .collect(toMap(
                        run -> run.getActions(MonitoringAction.class).get(0).getTraceId(), Run::getFullDisplayName));
        List<Observation> observations = List.copyOf(ContextLeakProbeTestStep.OBSERVATIONS);
        assertEquals(2 * PROBE_BUILDS * PROBES_PER_BUILD, observations.size(), observations::toString);
        List<String> wrong = observations.stream()
                .filter(observation -> !observation.inOwnBuild())
                .map(observation -> describe(observation, buildOfTrace))
                .toList();
        assertTrue(
                wrong.isEmpty(),
                () -> wrong.size() + " of " + observations.size()
                        + " block steps started in a context that is not one of their build:\n"
                        + String.join("\n", wrong));
    }

    private static final String PROGRAM_PROBE = """
            import io.jenkins.plugins.opentelemetry.job.step.ContextLeakProgramProbe
            ContextLeakProgramProbe.record(env.BUILD_NUMBER, 'top')
            stage('build') {
              ContextLeakProgramProbe.record(env.BUILD_NUMBER, 'in stage')
              withEnv(['X=1']) {
                ContextLeakProgramProbe.record(env.BUILD_NUMBER, 'in withEnv')
              }
              echo 'a synchronous step'
              ContextLeakProgramProbe.record(env.BUILD_NUMBER, 'after echo')
            }
            ContextLeakProgramProbe.record(env.BUILD_NUMBER, 'after stage')
            parallel a: {
              ContextLeakProgramProbe.record(env.BUILD_NUMBER, 'branch a')
            }, b: {
              ContextLeakProgramProbe.record(env.BUILD_NUMBER, 'branch b')
            }
            ContextLeakProgramProbe.record(env.BUILD_NUMBER, 'after parallel')
            sleep time: 10, unit: 'MILLISECONDS'
            ContextLeakProgramProbe.record(env.BUILD_NUMBER, 'after sleep')
            """;

    /** The span that each probe of {@link #PROGRAM_PROBE} must run in: the one of its block. */
    private static final Map<String, String> PROGRAM_PROBE_SPANS = Map.of(
            "top", ExtendedJenkinsAttributes.JENKINS_JOB_SPAN_PHASE_RUN_NAME,
            "in stage", "Stage: build",
            "in withEnv", "Stage: build",
            "after echo", "Stage: build",
            "after stage", ExtendedJenkinsAttributes.JENKINS_JOB_SPAN_PHASE_RUN_NAME,
            "branch a", "Parallel branch: a",
            "branch b", "Parallel branch: b",
            "after parallel", ExtendedJenkinsAttributes.JENKINS_JOB_SPAN_PHASE_RUN_NAME,
            "after sleep", ExtendedJenkinsAttributes.JENKINS_JOB_SPAN_PHASE_RUN_NAME);

    /**
     * The Groovy code of a trusted pipeline script, between its steps, runs in the span of its block, of its own build:
     * not in the span of the step before it, and not in a span of a build that ran on the same CPS VM thread before.
     * The asynchronous graph listeners of a build run in a span of that build.
     */
    @Test
    public void testProgramCodeRunsInTheSpanOfItsBlock() throws Exception {
        ContextLeakProgramProbe.OBSERVATIONS.clear();
        AsyncGraphListenerProbe.OBSERVATIONS.clear();
        WorkflowJob blocked = pipeline("program-blocked", "contextLeakBlockTestStep 'program-first-task'");
        WorkflowJob probe = pipeline("program-probe", PROGRAM_PROBE, false);
        QueueTaskFuture<WorkflowRun> blockedBuild = blocked.scheduleBuild2(0);
        ContextLeakBlockTestStep.waitForStart("program-first-task");
        List<WorkflowRun> runs = new ArrayList<>();
        for (int i = 0; i < PROBE_BUILDS; i++) {
            runs.add(jenkinsRule.buildAndAssertSuccess(probe));
        }
        ContextLeakBlockTestStep.release("program-first-task");
        WorkflowRun blockedRun = jenkinsRule.assertBuildStatusSuccess(blockedBuild);

        Map<String, String> traceOfBuild = runs.stream()
                .collect(toMap(
                        run -> String.valueOf(run.getNumber()),
                        run -> run.getActions(MonitoringAction.class).get(0).getTraceId()));
        List<ContextLeakProgramProbe.Observation> observations = List.copyOf(ContextLeakProgramProbe.OBSERVATIONS);
        assertEquals(PROBE_BUILDS * PROGRAM_PROBE_SPANS.size(), observations.size(), observations::toString);
        List<String> wrong = observations.stream()
                .filter(o -> !(o.current().isValid()
                        && o.recording()
                        && o.current().getTraceId().equals(traceOfBuild.get(o.build()))
                        && PROGRAM_PROBE_SPANS.get(o.id()).equals(o.spanName())))
                .map(o -> "  build #" + o.build() + ", " + o.id() + ", thread '" + o.thread() + "': "
                        + (o.current().isValid()
                                ? "span '" + o.spanName() + "'" + (o.recording() ? "" : " (ended)")
                                        + (o.current().getTraceId().equals(traceOfBuild.get(o.build()))
                                                ? ""
                                                : " of another trace")
                                : "no span")
                        + ", expected '" + PROGRAM_PROBE_SPANS.get(o.id()) + "'")
                .toList();
        assertTrue(
                wrong.isEmpty(),
                () -> wrong.size() + " of " + observations.size() + " probes ran outside the span of their block:\n"
                        + String.join("\n", wrong));

        Map<String, String> traceOfRun = new java.util.HashMap<>();
        for (WorkflowRun run : runs) {
            traceOfRun.put(run.getFullDisplayName(), traceOfBuild.get(String.valueOf(run.getNumber())));
        }
        traceOfRun.put(
                blockedRun.getFullDisplayName(),
                blockedRun.getActions(MonitoringAction.class).get(0).getTraceId());
        List<AsyncGraphListenerProbe.Notification> notifications = List.copyOf(AsyncGraphListenerProbe.OBSERVATIONS);
        assertTrue(notifications.stream().anyMatch(n -> n.current().isValid()), notifications::toString);
        List<String> elsewhere = notifications.stream()
                .filter(n -> n.current().isValid()
                        && !(n.recording() && n.current().getTraceId().equals(traceOfRun.get(n.run()))))
                .map(Object::toString)
                .toList();
        assertTrue(
                elsewhere.isEmpty(),
                () -> "asynchronous graph listener notifications in a span that is not one of their build:\n"
                        + String.join("\n", elsewhere));
    }

    private WorkflowJob pipeline(String name, String script) throws Exception {
        return pipeline(name, script, true);
    }

    private WorkflowJob pipeline(String name, String script, boolean sandbox) throws Exception {
        WorkflowJob job = jenkinsRule.createProject(
                WorkflowJob.class, "cps-vm-context-" + name + "-" + jobNameSuffix.incrementAndGet());
        job.setDefinition(new CpsFlowDefinition(script, sandbox));
        return job;
    }

    /** Records the context that the asynchronous graph listeners of the builds of this test run in. */
    @TestExtension("testProgramCodeRunsInTheSpanOfItsBlock")
    public static class AsyncGraphListenerProbe implements GraphListener {
        static final List<Notification> OBSERVATIONS = new CopyOnWriteArrayList<>();

        @Override
        public void onNewHead(FlowNode node) {
            try {
                Queue.Executable executable = node.getExecution().getOwner().getExecutable();
                if (executable instanceof WorkflowRun run
                        && run.getParent().getName().startsWith("cps-vm-context-program-")) {
                    Span current = Span.current();
                    OBSERVATIONS.add(new Notification(
                            run.getFullDisplayName(), node.getId(), current.getSpanContext(), current.isRecording()));
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        record Notification(String run, String node, SpanContext current, boolean recording) {}
    }

    private static String describe(Observation observation, Map<String, String> buildOfTrace) {
        String context = observation.current().isValid()
                ? "span " + observation.current().getSpanId() + (observation.recording() ? "" : " (ended)") + " of "
                        + buildOfTrace.getOrDefault(
                                observation.current().getTraceId(),
                                "trace " + observation.current().getTraceId())
                : "no span";
        return "  " + observation.run() + ", step " + observation.id() + ", thread '" + observation.thread() + "': "
                + context;
    }
}
