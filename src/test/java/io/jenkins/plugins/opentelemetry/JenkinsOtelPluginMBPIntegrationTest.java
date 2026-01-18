/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.junit.Assume.assumeFalse;

import com.github.rutledgepaulv.prune.Tree;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.Attributes;
import java.util.List;
import java.util.Optional;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitSCMSource;
import org.apache.commons.lang3.SystemUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Test;

public class JenkinsOtelPluginMBPIntegrationTest extends BaseIntegrationTest {

    @Test
    public void testMultibranchPipelineStep() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        String pipelineScript = """
            pipeline {
              agent any
              stages {
                stage('foo') {
                  steps {
                    echo 'hello world'\s
                    script {\s
                      currentBuild.description = 'Bar'\s
                    }\s
                  }
                }
              }
            }""";
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", pipelineScript);
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        final String mbpName = "test-pipeline-with-node-steps-" + jobNameSuffix.incrementAndGet();
        final String branchName = "master";
        WorkflowMultiBranchProject mp = jenkinsRule.createProject(WorkflowMultiBranchProject.class, mbpName);
        mp.getSourcesList()
                .add(new BranchSource(
                        new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
                        new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = scheduleAndFindBranchProject(mp, branchName);
        jenkinsRule.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        final String jobName = mbpName + "/" + branchName;

        String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        // TODO: support the chain of spans for the checkout step (it uses some random folder name in the tests
        checkChainOfSpans(spans, "Stage: Declarative: Checkout SCM", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run");
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(9L));

        List<SpanDataWrapper> root = spans.byDepth().get(0);
        Attributes attributes = root.get(0).spanData.getAttributes();
        MatcherAssert.assertThat(
                attributes.get(ExtendedJenkinsAttributes.CI_PIPELINE_MULTIBRANCH_TYPE),
                CoreMatchers.is(OtelUtils.BRANCH));
        MatcherAssert.assertThat(
                attributes.get(ExtendedJenkinsAttributes.CI_PIPELINE_TYPE), CoreMatchers.is(OtelUtils.MULTIBRANCH));
        MatcherAssert.assertThat(
                attributes.get(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_DESCRIPTION), CoreMatchers.is("Bar"));
        MatcherAssert.assertThat(
                attributes.get(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_CAUSE),
                CoreMatchers.is(List.of("BranchIndexingCause")));

        // TODO: support the chain of spans for the checkout step (it uses some random folder name in the tests
        // It returns the first checkout, aka the one without any shallow cloning, depth shallow.
        Optional<Tree.Node<SpanDataWrapper>> checkoutNode = spans.breadthFirstSearchNodes(
                node -> node.getData().spanData.getName().startsWith("checkout:"));
        MatcherAssert.assertThat(checkoutNode, CoreMatchers.is(CoreMatchers.notNullValue()));

        attributes = checkoutNode.get().getData().spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_CLONE_SHALLOW), CoreMatchers.is(false));
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_CLONE_DEPTH), CoreMatchers.is(0L));

        // TODO verify environment variables are populated in shell steps
    }
}
