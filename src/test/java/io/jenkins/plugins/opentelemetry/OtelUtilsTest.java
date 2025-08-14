/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitSCMSource;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.Test;

class OtelUtilsTest extends BaseIntegrationTest {

    @Test
    void test_workflow() throws Exception {
        final String jobName = "test-simple-pipeline-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition("echo 'hi'", true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        assertThat(OtelUtils.isFreestyle(build), is(false));
        assertThat(OtelUtils.isMatrix(build), is(false));
        assertThat(OtelUtils.isMaven(build), is(false));
        assertThat(OtelUtils.isMultibranch(build), is(false));
        assertThat(OtelUtils.isWorkflow(build), is(true));
        assertThat(OtelUtils.getProjectType(build), is(OtelUtils.WORKFLOW));
        assertThat(OtelUtils.getMultibranchType(build), is(OtelUtils.UNKNOWN));
        assertThat(OtelUtils.isMultibranchChangeRequest(build), is(false));
        assertThat(OtelUtils.isMultibranchBranch(build), is(false));
        assertThat(OtelUtils.isMultibranchTag(build), is(false));
    }

    @Test
    void test_freestyle() throws Exception {
        final String jobName = "test-freestyle-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

        assertThat(OtelUtils.isFreestyle(build), is(true));
        assertThat(OtelUtils.isMatrix(build), is(false));
        assertThat(OtelUtils.isMaven(build), is(false));
        assertThat(OtelUtils.isMultibranch(build), is(false));
        assertThat(OtelUtils.isWorkflow(build), is(false));
        assertThat(OtelUtils.getProjectType(build), is(OtelUtils.FREESTYLE));
        assertThat(OtelUtils.getMultibranchType(build), is(OtelUtils.UNKNOWN));
        assertThat(OtelUtils.isMultibranchChangeRequest(build), is(false));
        assertThat(OtelUtils.isMultibranchBranch(build), is(false));
        assertThat(OtelUtils.isMultibranchTag(build), is(false));
    }

    @Test
    void test_matrix() throws Exception {
        // See
        // https://github.com/jenkinsci/matrix-project-plugin/blob/be0b18bcba0c4089b1ed9482863050de6aa65b32/src/test/java/hudson/matrix/MatrixProjectTest.java#L193-L202
        final String jobName = "test-matrix-" + jobNameSuffix.incrementAndGet();
        MatrixProject project = jenkinsRule.createProject(MatrixProject.class, jobName);
        AxisList axes = new AxisList();
        axes.add(new TextAxis("db", "mysql", "oracle"));
        axes.add(new TextAxis("direction", "north", "south"));
        project.setAxes(axes);
        MatrixBuild build = jenkinsRule.buildAndAssertSuccess(project);

        assertThat(OtelUtils.isFreestyle(build), is(false));
        assertThat(OtelUtils.isMatrix(build), is(true));
        assertThat(OtelUtils.isMaven(build), is(false));
        assertThat(OtelUtils.isMultibranch(build), is(false));
        assertThat(OtelUtils.isWorkflow(build), is(false));
        assertThat(OtelUtils.getProjectType(build), is(OtelUtils.MATRIX));
        assertThat(OtelUtils.getMultibranchType(build), is(OtelUtils.UNKNOWN));
        assertThat(OtelUtils.isMultibranchChangeRequest(build), is(false));
        assertThat(OtelUtils.isMultibranchBranch(build), is(false));
        assertThat(OtelUtils.isMultibranchTag(build), is(false));
    }

    @Test
    void test_maven() throws Exception {
        final String jobName = "test-maven-" + jobNameSuffix.incrementAndGet();
        MavenModuleSet project = jenkinsRule.createProject(MavenModuleSet.class, jobName);
        // Maven installation has not been done so it will fail, but the test should only validate
        // the methods.
        MavenModuleSetBuild build = jenkinsRule.buildAndAssertStatus(Result.FAILURE, project);

        assertThat(OtelUtils.isFreestyle(build), is(false));
        assertThat(OtelUtils.isMatrix(build), is(false));
        assertThat(OtelUtils.isMaven(build), is(true));
        assertThat(OtelUtils.isMultibranch(build), is(false));
        assertThat(OtelUtils.isWorkflow(build), is(false));
        assertThat(OtelUtils.getProjectType(build), is(OtelUtils.MAVEN));
        assertThat(OtelUtils.getMultibranchType(build), is(OtelUtils.UNKNOWN));
        assertThat(OtelUtils.isMultibranchChangeRequest(build), is(false));
        assertThat(OtelUtils.isMultibranchBranch(build), is(false));
        assertThat(OtelUtils.isMultibranchTag(build), is(false));
    }

    @Test
    void test_multibranch() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo 'hi'");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        final String mbpName = "node-steps-" + jobNameSuffix.incrementAndGet();
        final String branchName = "master";
        WorkflowMultiBranchProject mp = jenkinsRule.createProject(WorkflowMultiBranchProject.class, mbpName);
        mp.getSourcesList()
                .add(new BranchSource(
                        new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
                        new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = scheduleAndFindBranchProject(mp, branchName);
        jenkinsRule.waitUntilNoActivity();
        WorkflowRun build = p.getLastBuild();

        assertThat(OtelUtils.isFreestyle(build), is(false));
        assertThat(OtelUtils.isMatrix(build), is(false));
        assertThat(OtelUtils.isMaven(build), is(false));
        assertThat(OtelUtils.isMultibranch(build), is(true));
        assertThat(OtelUtils.isWorkflow(build), is(false));
        assertThat(OtelUtils.getProjectType(build), is(OtelUtils.MULTIBRANCH));
        assertThat(OtelUtils.getMultibranchType(build), is(OtelUtils.BRANCH));
        assertThat(OtelUtils.isMultibranchChangeRequest(build), is(false));
        assertThat(OtelUtils.isMultibranchBranch(build), is(true));
        assertThat(OtelUtils.isMultibranchTag(build), is(false));
    }

    @Test
    void test_null() {
        assertThat(OtelUtils.isFreestyle(null), is(false));
        assertThat(OtelUtils.isMatrix(null), is(false));
        assertThat(OtelUtils.isMaven(null), is(false));
        assertThat(OtelUtils.isMultibranch(null), is(false));
        assertThat(OtelUtils.isWorkflow(null), is(false));
        assertThat(OtelUtils.getProjectType(null), is(OtelUtils.UNKNOWN));
        assertThat(OtelUtils.getMultibranchType(null), is(OtelUtils.UNKNOWN));
        assertThat(OtelUtils.isMultibranchChangeRequest(null), is(false));
        assertThat(OtelUtils.isMultibranchBranch(null), is(false));
        assertThat(OtelUtils.isMultibranchTag(null), is(false));
    }
}
