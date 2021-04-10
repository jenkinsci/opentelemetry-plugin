/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.github.rutledgepaulv.prune.Tree;
import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
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
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

public class JenkinsOtelPluginMBPIntegrationTest extends BaseIntegrationTest {

    @Test
    public void testMultibranchPipelineStep() throws Exception {
        String pipelineScript = "pipeline {\n" +
                "  agent any\n" +
                "  stages {\n" +
                "    stage('foo') {\n" +
                "      steps {\n" +
                "        echo 'hello world' \n" +
                "        script { \n" +
                "          currentBuild.description = 'Bar' \n" +
                "        } \n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", pipelineScript);
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        final String mbpName = "test-pipeline-with-node-steps-" + jobNameSuffix.incrementAndGet();
        final String branchName = "master";
        WorkflowMultiBranchProject mp = jenkinsRule.createProject(WorkflowMultiBranchProject.class, mbpName);
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = scheduleAndFindBranchProject(mp, branchName);
        jenkinsRule.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        final String jobName = mbpName + "/" + branchName;
        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", jobName);
        // TODO: support the chain of spans for the checkout step (it uses some random folder name in the tests
        checkChainOfSpans(spans, "Stage: Declarative: Checkout SCM", "Node", "Phase: Run");
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(9L));

        List<SpanDataWrapper> root = spans.byDepth().get(0);
        Attributes attributes = root.get(0).spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.CI_PIPELINE_MULTIBRANCH_TYPE), CoreMatchers.is(OtelUtils.BRANCH));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.CI_PIPELINE_TYPE), CoreMatchers.is(OtelUtils.MULTIBRANCH));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_DESCRIPTION), CoreMatchers.is("Bar"));
    }

    @Test
    @Ignore("Requires releases from the dependent plugins")
    public void testPipelineWithMetadata() throws Exception {
        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
                "node() {\n" +
                "    stage('ze-stage') {\n" +
                "       xsh (label: 'shell-1', script: 'echo ze-echo-1', apm: [ 'net.peer.name': 'my.foo', 'rpc.system': 'http']) \n" +
                "    }\n" +
                "}";
        final String jobName = "test-pipeline-with-metadata-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", jobName);
        checkChainOfSpans(spans, "shell-1", "Stage: ze-stage1", "Node", "Phase: Run");
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(10L));
        // TODO: validate whether the span attributes are properly added.
    }
}
