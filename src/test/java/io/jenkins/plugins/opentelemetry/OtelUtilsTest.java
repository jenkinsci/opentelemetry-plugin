/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.junit.Assume.assumeFalse;

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
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Test;

public class OtelUtilsTest extends BaseIntegrationTest {

    @Test
    public void test_workflow() throws Exception {
        final String jobName = "test-simple-pipeline-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition("echo 'hi'", true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        MatcherAssert.assertThat(OtelUtils.isFreestyle(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMatrix(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMaven(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranch(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isWorkflow(build), CoreMatchers.is(true));
        MatcherAssert.assertThat(OtelUtils.getProjectType(build), CoreMatchers.is(OtelUtils.WORKFLOW));
        MatcherAssert.assertThat(OtelUtils.getMultibranchType(build), CoreMatchers.is(OtelUtils.UNKNOWN));
        MatcherAssert.assertThat(OtelUtils.isMultibranchChangeRequest(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchBranch(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchTag(build), CoreMatchers.is(false));
    }

    @Test
    public void test_freestyle() throws Exception {
        final String jobName = "test-freestyle-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

        MatcherAssert.assertThat(OtelUtils.isFreestyle(build), CoreMatchers.is(true));
        MatcherAssert.assertThat(OtelUtils.isMatrix(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMaven(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranch(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isWorkflow(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.getProjectType(build), CoreMatchers.is(OtelUtils.FREESTYLE));
        MatcherAssert.assertThat(OtelUtils.getMultibranchType(build), CoreMatchers.is(OtelUtils.UNKNOWN));
        MatcherAssert.assertThat(OtelUtils.isMultibranchChangeRequest(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchBranch(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchTag(build), CoreMatchers.is(false));
    }

    @Test
    public void test_matrix() throws Exception {
        // See https://github.com/jenkinsci/matrix-project-plugin/blob/be0b18bcba0c4089b1ed9482863050de6aa65b32/src/test/java/hudson/matrix/MatrixProjectTest.java#L193-L202
        final String jobName = "test-matrix-" + jobNameSuffix.incrementAndGet();
        MatrixProject project = jenkinsRule.createProject(MatrixProject.class, jobName);
        AxisList axes = new AxisList();
        axes.add(new TextAxis("db","mysql", "oracle"));
        axes.add(new TextAxis("direction","north", "south"));
        project.setAxes(axes);
        MatrixBuild build = jenkinsRule.buildAndAssertSuccess(project);

        MatcherAssert.assertThat(OtelUtils.isFreestyle(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMatrix(build), CoreMatchers.is(true));
        MatcherAssert.assertThat(OtelUtils.isMaven(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranch(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isWorkflow(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.getProjectType(build), CoreMatchers.is(OtelUtils.MATRIX));
        MatcherAssert.assertThat(OtelUtils.getMultibranchType(build), CoreMatchers.is(OtelUtils.UNKNOWN));
        MatcherAssert.assertThat(OtelUtils.isMultibranchChangeRequest(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchBranch(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchTag(build), CoreMatchers.is(false));
    }

    @Test
    public void test_maven() throws Exception {
        final String jobName = "test-maven-" + jobNameSuffix.incrementAndGet();
        MavenModuleSet project = jenkinsRule.createProject(MavenModuleSet.class, jobName);
        // Maven installation has not been done so it will fail, but the test should only validate
        // the methods.
        MavenModuleSetBuild build = jenkinsRule.buildAndAssertStatus(Result.FAILURE, project);

        MatcherAssert.assertThat(OtelUtils.isFreestyle(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMatrix(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMaven(build), CoreMatchers.is(true));
        MatcherAssert.assertThat(OtelUtils.isMultibranch(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isWorkflow(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.getProjectType(build), CoreMatchers.is(OtelUtils.MAVEN));
        MatcherAssert.assertThat(OtelUtils.getMultibranchType(build), CoreMatchers.is(OtelUtils.UNKNOWN));
        MatcherAssert.assertThat(OtelUtils.isMultibranchChangeRequest(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchBranch(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchTag(build), CoreMatchers.is(false));
    }

    @Test
    public void test_multibranch() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo 'hi'");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        final String mbpName = "node-steps-" + jobNameSuffix.incrementAndGet();
        final String branchName = "master";
        WorkflowMultiBranchProject mp = jenkinsRule.createProject(WorkflowMultiBranchProject.class, mbpName);
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = scheduleAndFindBranchProject(mp, branchName);
        jenkinsRule.waitUntilNoActivity();
        WorkflowRun build = p.getLastBuild();

        MatcherAssert.assertThat(OtelUtils.isFreestyle(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMatrix(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMaven(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranch(build), CoreMatchers.is(true));
        MatcherAssert.assertThat(OtelUtils.isWorkflow(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.getProjectType(build), CoreMatchers.is(OtelUtils.MULTIBRANCH));
        MatcherAssert.assertThat(OtelUtils.getMultibranchType(build), CoreMatchers.is(OtelUtils.BRANCH));
        MatcherAssert.assertThat(OtelUtils.isMultibranchChangeRequest(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchBranch(build), CoreMatchers.is(true));
        MatcherAssert.assertThat(OtelUtils.isMultibranchTag(build), CoreMatchers.is(false));
    }

    @Test
    public void test_null() throws Exception {
        MatcherAssert.assertThat(OtelUtils.isFreestyle(null), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMatrix(null), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMaven(null), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranch(null), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isWorkflow(null), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.getProjectType(null), CoreMatchers.is(OtelUtils.UNKNOWN));
        MatcherAssert.assertThat(OtelUtils.getMultibranchType(null), CoreMatchers.is(OtelUtils.UNKNOWN));
        MatcherAssert.assertThat(OtelUtils.isMultibranchChangeRequest(null), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchBranch(null), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchTag(null), CoreMatchers.is(false));
    }
}
