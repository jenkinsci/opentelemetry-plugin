package io.jenkins.plugins.opentelemetry.steps;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class CreateSpanStepTest {

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test
    public void test_with_attributes_in_a_string_map() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "node { createSpan(name: 'my-span') { echo 'here' } }", true));
                WorkflowRun b = story.j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0).get());
                story.j.assertLogContains("here", b);
            }
        });
    }

    @Test
    public void test_with_attributes_in_a_attributesValue_structure() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "node { createSpan(name: 'my-span', attributes: [[key: 'attribute.foo', value: 'foo'], [key: 'attribute.bar', value: 'bar']]) { echo 'here' } }", true));
                WorkflowRun b = story.j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0).get());
                story.j.assertLogContains("here", b);
            }
        });
    }
}
