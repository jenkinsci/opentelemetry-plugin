/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitSCMSource;
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
        MatcherAssert.assertThat(OtelUtils.isMultibranch(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isWorkflow(build), CoreMatchers.is(true));
        MatcherAssert.assertThat(OtelUtils.getProjectType(build), CoreMatchers.is("workflow"));
        MatcherAssert.assertThat(OtelUtils.getMultibranchType(build), CoreMatchers.is("unknown"));
        MatcherAssert.assertThat(OtelUtils.isMultibranchPullRequest(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchBranch(build), CoreMatchers.is(false));
    }

    @Test
    public void test_freestyle() throws Exception {
        final String jobName = "test-freestyle-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

        MatcherAssert.assertThat(OtelUtils.isFreestyle(build), CoreMatchers.is(true));
        MatcherAssert.assertThat(OtelUtils.isMultibranch(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isWorkflow(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.getProjectType(build), CoreMatchers.is("freestyle"));
        MatcherAssert.assertThat(OtelUtils.getMultibranchType(build), CoreMatchers.is("unknown"));
        MatcherAssert.assertThat(OtelUtils.isMultibranchPullRequest(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchBranch(build), CoreMatchers.is(false));
    }

    @Test
    public void test_multibranch() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo 'hi'");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        final String mbpName = "test-pipeline-with-node-steps-" + jobNameSuffix.incrementAndGet();
        final String branchName = "master";
        WorkflowMultiBranchProject mp = jenkinsRule.createProject(WorkflowMultiBranchProject.class, mbpName);
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = scheduleAndFindBranchProject(mp, branchName);
        jenkinsRule.waitUntilNoActivity();
        WorkflowRun build = p.getLastBuild();

        MatcherAssert.assertThat(OtelUtils.isFreestyle(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranch(build), CoreMatchers.is(true));
        MatcherAssert.assertThat(OtelUtils.isWorkflow(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.getProjectType(build), CoreMatchers.is("multibranch"));
        MatcherAssert.assertThat(OtelUtils.getMultibranchType(build), CoreMatchers.is("branch"));
        MatcherAssert.assertThat(OtelUtils.isMultibranchPullRequest(build), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchBranch(build), CoreMatchers.is(true));
    }

    @Test
    public void test_null() throws Exception {
        MatcherAssert.assertThat(OtelUtils.isFreestyle(null), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranch(null), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isWorkflow(null), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.getProjectType(null), CoreMatchers.is("unknown"));
        MatcherAssert.assertThat(OtelUtils.getMultibranchType(null), CoreMatchers.is("unknown"));
        MatcherAssert.assertThat(OtelUtils.isMultibranchPullRequest(null), CoreMatchers.is(false));
        MatcherAssert.assertThat(OtelUtils.isMultibranchBranch(null), CoreMatchers.is(false));
    }
}
