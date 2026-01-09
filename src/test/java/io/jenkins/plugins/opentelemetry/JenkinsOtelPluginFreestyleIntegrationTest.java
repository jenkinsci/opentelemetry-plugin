/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.hamcrest.Matchers.is;
import static io.jenkins.plugins.opentelemetry.OtelUtils.JENKINS_CORE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.github.rutledgepaulv.prune.Tree;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.tasks.Ant;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.Shell;
import hudson.tasks._ant.AntTargetNote;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.Attributes;
import java.io.File;
import java.util.List;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.FakeChangeLogSCM;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.ToolInstallations;
import org.jvnet.hudson.test.recipes.WithPlugin;

class JenkinsOtelPluginFreestyleIntegrationTest extends BaseIntegrationTest {

    @TempDir
    private File tmp;

    private boolean antTargetNoteEnabled;

    @BeforeEach
    void setUp() {
        antTargetNoteEnabled = AntTargetNote.ENABLED;
    }

    @AfterEach
    void tearDown() {
        AntTargetNote.ENABLED = antTargetNoteEnabled;
    }

    @Test
    void testFreestyleJob() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-freestyle-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.getBuildersList().add(new Shell("set -u && touch \"x\""));
        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

        String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, "shell", "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);
        assertThat(spans.cardinality(), is(5L));

        assertFreestyleJobMetadata(build, spans);
        assertBuildStepMetadata(spans, "shell", JENKINS_CORE);
        assertNodeMetadata(spans, rootSpanName, false);
    }

    @Test
    void testFreestyleJob_with_multiple_builders() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-multiple-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.getBuildersList().add(new Shell("set -u && touch \"x\""));
        project.getBuildersList().add(new Shell("set -u && touch \"y\""));
        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

        String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, "shell", "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "shell", "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);
        assertThat(spans.cardinality(), is(6L));

        assertFreestyleJobMetadata(build, spans);
        assertBuildStepMetadata(spans, "shell", JENKINS_CORE);
        assertNodeMetadata(spans, rootSpanName, false);
    }

    @Test
    void testFreestyleJobFailed() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-failed-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.getBuildersList().add(new Shell("set -u && exit 1"));
        FreeStyleBuild build = jenkinsRule.buildAndAssertStatus(Result.FAILURE, project);

        String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, "shell", "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);
        assertThat(spans.cardinality(), is(5L));

        assertFreestyleJobMetadata(build, spans);
        assertBuildStepMetadata(spans, "shell", JENKINS_CORE);
        assertNodeMetadata(spans, rootSpanName, false);
    }

    @Test
    void testFreestyleJob_with_publishers() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        String jobName = "test-publisher-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.getBuildersList().add(new Shell("set -u && touch \"test.txt\""));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(false);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

        String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, "shell", "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "archiveArtifacts", "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);
        assertThat(spans.cardinality(), is(6L));

        assertFreestyleJobMetadata(build, spans);
        assertBuildStepMetadata(spans, "shell", JENKINS_CORE);
        assertNodeMetadata(spans, rootSpanName, false);
    }

    @Test
    void testFreestyleJob_with_assigned_node() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-assigned-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.getBuildersList().add(new Shell("set -u && exit 0"));
        final Node agent = jenkinsRule.createOnlineSlave();
        try {
            agent.setLabelString("linux");
            project.setAssignedNode(agent);
            FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

            String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

            Tree<SpanDataWrapper> spans = getBuildTrace();
            checkChainOfSpans(spans, "Phase: Start", rootSpanName);
            checkChainOfSpans(spans, "shell", "Phase: Run", rootSpanName);
            checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);
            assertThat(spans.cardinality(), is(5L));

            assertFreestyleJobMetadata(build, spans);
            assertBuildStepMetadata(spans, "shell", JENKINS_CORE);
            assertNodeMetadata(spans, rootSpanName, true);
        } finally {
            jenkinsRule.jenkins.removeNode(agent);
        }
    }

    @Test
    void testFreestyleJob_with_causes() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-cause-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.getBuildersList().add(new Shell("set -u && touch \"x\""));
        jenkinsRule.assertBuildStatusSuccess(project.scheduleBuild2(0, new Cause.UserIdCause()));
        Tree<SpanDataWrapper> spans = getBuildTrace();
        List<SpanDataWrapper> root = spans.byDepth().get(0);
        Attributes attributes = root.get(0).spanData().getAttributes();
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_CAUSE),
                is(List.of("UserIdCause:SYSTEM")));
    }

    @Test
    @WithPlugin("ant")
    void testFreestyleJob_with_ant_plugin() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-ant-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.setScm(new SingleFileSCM(
                "build.xml",
                io.jenkins.plugins.opentelemetry.JenkinsOtelPluginFreestyleIntegrationTest.class.getResource(
                        "ant.xml")));
        String antName = configureDefaultAnt().getName();
        project.getBuildersList().add(new Ant("foo", antName, null, null, null));
        final Node agent = jenkinsRule.createOnlineSlave();
        try {
            agent.setLabelString("ant");
            project.setAssignedNode(agent);
            FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

            String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

            Tree<SpanDataWrapper> spans = getBuildTrace();
            checkChainOfSpans(spans, "Phase: Start", rootSpanName);
            checkChainOfSpans(spans, "ant", "Phase: Run", rootSpanName);
            checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);
            assertThat(spans.cardinality(), is(5L));

            assertFreestyleJobMetadata(build, spans);
            assertBuildStepMetadata(spans, "ant", "ant");
            assertNodeMetadata(spans, rootSpanName, true);
        } finally {
            jenkinsRule.jenkins.removeNode(agent);
        }
    }

    // See
    // https://github.com/jenkinsci/ant-plugin/blob/582cf994e7834816665150aad1731fbe8a67be4d/src/test/java/hudson/tasks/AntTest.java
    private Ant.AntInstallation configureDefaultAnt() throws Exception {
        TemporaryFolder temp = new TemporaryFolder(tmp);
        temp.create();
        return ToolInstallations.configureDefaultAnt(temp);
    }

    @Test
    void testFreestyleJob_with_culprits() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // See
        // https://github.com/abayer/jenkins/blob/914963c22317e7d72cf7e3e7d9ed8ab57709ccb0/test/src/test/java/hudson/model/AbstractBuildTest.java#L135-L150

        final String jobName = "test-freestyle-culprits-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        FakeChangeLogSCM scm = new FakeChangeLogSCM();
        project.setScm(scm);

        // 1st build, successful, no culprits
        scm.addChange().withAuthor("alice");
        jenkinsRule.buildAndAssertSuccess(project);

        String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(spans, "Phase: Run", rootSpanName);

        // 2nd build
        scm.addChange().withAuthor("bob");
        project.getBuildersList().add(new FailureBuilder());
        jenkinsRule.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        spans = getBuildTrace(1);
        checkChainOfSpans(spans, "Phase: Run", rootSpanName);

        // 3rd build. bob continues to be in culprit
        project.getBuildersList().add(new FailureBuilder());
        scm.addChange().withAuthor("charlie");
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        spans = getBuildTrace(2);

        checkChainOfSpans(spans, "Phase: Run", rootSpanName);

        List<SpanDataWrapper> root = spans.byDepth().get(0);
        Attributes attributes = root.get(0).spanData().getAttributes();
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_COMMITTERS), is(List.of("bob")));

        assertFreestyleJobMetadata(build, spans);
    }
}
