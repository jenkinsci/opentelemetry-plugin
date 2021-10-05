/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.github.rutledgepaulv.prune.Tree;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.tasks.Ant;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.Shell;
import hudson.tasks._ant.AntTargetNote;
import org.apache.commons.lang3.SystemUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.ToolInstallations;
import org.jvnet.hudson.test.recipes.WithPlugin;

import static org.junit.Assume.assumeFalse;

public class JenkinsOtelPluginFreestyleIntegrationTest extends BaseIntegrationTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public TestRule antTargetNoteEnabled = new FlagRule<Boolean>(() -> AntTargetNote.ENABLED, x -> AntTargetNote.ENABLED = x);

    @Test
    public void testFreestyleJob() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-freestyle-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.getBuildersList().add(new Shell("set -u && touch \"x\""));
        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", jobName);
        checkChainOfSpans(spans, "shell", "Phase: Run", jobName);
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(5L));

        assertFreestyleJobMetadata(build, spans);
        assertNodeMetadata(spans, jobName, false);
    }

    @Test
    public void testFreestyleJob_with_multiple_builders() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-freestyle-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.getBuildersList().add(new Shell("set -u && touch \"x\""));
        project.getBuildersList().add(new Shell("set -u && touch \"y\""));
        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", jobName);
        checkChainOfSpans(spans, "shell", "Phase: Run", jobName);
        checkChainOfSpans(spans, "shell", "Phase: Run", jobName);
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(6L));

        assertFreestyleJobMetadata(build, spans);
        assertNodeMetadata(spans, jobName, false);
    }

    @Test
    public void testFreestyleJobFailed() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-freestyle-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.getBuildersList().add(new Shell("set -u && exit 1"));
        FreeStyleBuild build = jenkinsRule.buildAndAssertStatus(Result.FAILURE, project);

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", jobName);
        checkChainOfSpans(spans, "shell", "Phase: Run", jobName);
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(5L));

        assertFreestyleJobMetadata(build, spans);
        assertNodeMetadata(spans, jobName, false);
    }

    @Test
    public void testFreestyleJob_with_publishers() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-freestyle-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.getBuildersList().add(new Shell("set -u && touch \"test.txt\""));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(false);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", jobName);
        checkChainOfSpans(spans, "shell", "Phase: Run", jobName);
        checkChainOfSpans(spans, "archiveArtifacts", "Phase: Run", jobName);
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(6L));

        assertFreestyleJobMetadata(build, spans);
        assertNodeMetadata(spans, jobName, false);
    }

    @Test
    public void testFreestyleJob_with_assigned_node() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-freestyle-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.getBuildersList().add(new Shell("set -u && exit 0"));
        final Node agent = jenkinsRule.createOnlineSlave();
        agent.setLabelString("linux");
        project.setAssignedNode(agent);
        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", jobName);
        checkChainOfSpans(spans, "shell", "Phase: Run", jobName);
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(5L));

        assertFreestyleJobMetadata(build, spans);
        assertNodeMetadata(spans, jobName, true);
    }

    @Test
    @WithPlugin("ant")
    public void testFreestyleJob_with_ant_plugin() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-freestyle-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.setScm(new SingleFileSCM("build.xml", io.jenkins.plugins.opentelemetry.JenkinsOtelPluginFreestyleIntegrationTest.class.getResource("ant.xml")));
        String antName = configureDefaultAnt().getName();
        project.getBuildersList().add(new Ant("foo", antName,null,null,null));
        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", jobName);
        checkChainOfSpans(spans, "ant", "Phase: Run", jobName);
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(5L));

        assertFreestyleJobMetadata(build, spans);
        assertNodeMetadata(spans, jobName, true);
    }

    // See https://github.com/jenkinsci/ant-plugin/blob/582cf994e7834816665150aad1731fbe8a67be4d/src/test/java/hudson/tasks/AntTest.java
    private Ant.AntInstallation configureDefaultAnt() throws Exception {
        return ToolInstallations.configureDefaultAnt(tmp);
    }
}
