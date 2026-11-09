/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.jenkins.plugins.opentelemetry.job.step.ContextLeakBlockTestStep;
import io.jenkins.plugins.opentelemetry.job.step.ContextLeakProbeTestStep;
import io.jenkins.plugins.opentelemetry.job.step.ContextLeakProbeTestStep.Observation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;

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

    private WorkflowJob pipeline(String name, String script) throws Exception {
        WorkflowJob job = jenkinsRule.createProject(
                WorkflowJob.class, "cps-vm-context-" + name + "-" + jobNameSuffix.incrementAndGet());
        job.setDefinition(new CpsFlowDefinition(script, true));
        return job;
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
