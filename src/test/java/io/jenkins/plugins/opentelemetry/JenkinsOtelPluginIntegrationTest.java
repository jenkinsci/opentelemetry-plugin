/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.github.rutledgepaulv.prune.Tree;
import com.google.common.collect.Iterables;
import hudson.EnvVars;
import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.util.LogTaskListener;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import org.jvnet.hudson.test.recipes.WithPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Note usage of `def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}}` is inspired by
 * https://github.com/jenkinsci/workflow-basic-steps-plugin/blob/474cea2a53753e1fb9b166fa1ca0f6184b5cee4a/src/test/java/org/jenkinsci/plugins/workflow/steps/IsUnixStepTest.java#L39
 */
public class JenkinsOtelPluginIntegrationTest extends BaseIntegrationTest {
    private static final Logger LOGGER = Logger.getLogger(Run.class.getName());

    @Test
    public void testSimplePipeline() throws Exception {
        // BEFORE

        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
                "node() {\n" +
                "    stage('ze-stage1') {\n" +
                "       xsh (label: 'shell-1', script: 'echo ze-echo-1') \n" +
                "    }\n" +
                "    stage('ze-stage2') {\n" +
                "       xsh (label: 'shell-2', script: 'echo ze-echo-2') \n" +
                "    }\n" +
                "}";
        final Node agent = jenkinsRule.createOnlineSlave();

        final String jobName = "test-simple-pipeline-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();

        checkChainOfSpans(spans, "Phase: Start", jobName);
        checkChainOfSpans(spans, JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI, JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", jobName);
        checkChainOfSpans(spans, "shell-1", "Stage: ze-stage1", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", jobName);
        checkChainOfSpans(spans, "shell-2", "Stage: ze-stage2", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", jobName);
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(10L));

        // WORKAROUND because we don't know how to force the IntervalMetricReader to collect metrics
        Thread.sleep(OpenTelemetrySdkProvider.TESTING_METRIC_EXPORTER_INTERVAL_MILLIS * 3);
        Map<String, MetricData> exportedMetrics = ((InMemoryMetricExporter) OpenTelemetrySdkProvider.TESTING_METRICS_EXPORTER).getLastExportedMetricByMetricName();
        dumpMetrics(exportedMetrics);
        MetricData runCompletedCounterData = exportedMetrics.get(JenkinsSemanticMetrics.CI_PIPELINE_RUN_COMPLETED);
        MatcherAssert.assertThat(runCompletedCounterData, CoreMatchers.notNullValue());
        // TODO TEST METRICS WITH PROPER RESET BETWEEN TESTS
        MatcherAssert.assertThat(runCompletedCounterData.getType(), CoreMatchers.is(MetricDataType.LONG_SUM));
        Collection<LongPointData> metricPoints = runCompletedCounterData.getLongSumData().getPoints();
        //MatcherAssert.assertThat(Iterables.getLast(metricPoints).getValue(), CoreMatchers.is(1L));
    }

    @Test
    @WithPlugin("cloudbees-disk-usage-simple")
    public void testMetricsWithDiskUsagePlugin() throws Exception {
        // WORKAROUND because we don't know how to force the IntervalMetricReader to collect metrics
        Thread.sleep(OpenTelemetrySdkProvider.TESTING_METRIC_EXPORTER_INTERVAL_MILLIS * 3);
        Map<String, MetricData> exportedMetrics = ((InMemoryMetricExporter) OpenTelemetrySdkProvider.TESTING_METRICS_EXPORTER).getLastExportedMetricByMetricName();
        dumpMetrics(exportedMetrics);
        MetricData diskUsageData = exportedMetrics.get(JenkinsSemanticMetrics.JENKINS_DISK_USAGE_BYTES);
        MatcherAssert.assertThat(diskUsageData, CoreMatchers.notNullValue());
        // TODO TEST METRICS WITH PROPER RESET BETWEEN TESTS
        MatcherAssert.assertThat(diskUsageData.getType(), CoreMatchers.is(MetricDataType.LONG_GAUGE));
        Collection<LongPointData> metricPoints = diskUsageData.getLongGaugeData().getPoints();
        MatcherAssert.assertThat(Iterables.getLast(metricPoints).getValue(), CoreMatchers.notNullValue());
    }

    @Test
    public void testTraceEnvironmentVariablesInjectedInShellSteps() throws Exception {
        if (Functions.isWindows()) {
            // TODO test on windows
        } else {
            String pipelineScript = "node() {\n" +
                    "    stage('ze-stage1') {\n" +
                    "       sh '''\n" +
                    "if [ -z $TRACEPARENT ]\n" +
                    "then\n" +
                    "   echo TRACEPARENT NOT FOUND\n" +
                    "   exit 1\n" +
                    "fi\n" +
                    "'''\n" +
                    "    }\n" +
                    "}";
            final Node node = jenkinsRule.createOnlineSlave();

            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-trace-environment-variables-injected-in-shell-steps-" + jobNameSuffix.incrementAndGet());
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

            final Tree<SpanDataWrapper> spans = getGeneratedSpans();
            MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(8L));
        }
    }

    @Test
    public void testPipelineWithNodeSteps() throws Exception {
        String pipelineScript = "pipeline {\n" +
                "  agent none\n" +
                "  stages {\n" +
                "    stage('foo') {\n" +
                "      steps {\n" +
                "        node('linux') { \n" +
                "          echo 'hello world' \n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        final Node agent = jenkinsRule.createOnlineSlave();
        agent.setLabelString("linux");

        final String jobName = "test-pipeline-with-node-steps-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", jobName);
        checkChainOfSpans(spans, JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI + ": linux", JenkinsOtelSemanticAttributes.AGENT_UI + ": linux", "Stage: foo", "Phase: Run");
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(7L));

        Optional<Tree.Node<SpanDataWrapper>> executorNodeAllocation = spans.breadthFirstSearchNodes(node -> (JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI + ": linux").equals(node.getData().spanData.getName()));
        MatcherAssert.assertThat(executorNodeAllocation, CoreMatchers.is(CoreMatchers.notNullValue()));

        Attributes attributes = executorNodeAllocation.get().getData().spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_AGENT_LABEL), CoreMatchers.is("linux"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION), CoreMatchers.is(CoreMatchers.notNullValue()));

        Optional<Tree.Node<SpanDataWrapper>> executorNode = spans.breadthFirstSearchNodes(node -> (JenkinsOtelSemanticAttributes.AGENT_UI + ": linux").equals(node.getData().spanData.getName()));
        MatcherAssert.assertThat(executorNode, CoreMatchers.is(CoreMatchers.notNullValue()));
        attributes = executorNode.get().getData().spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_AGENT_LABEL), CoreMatchers.is("linux"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION), CoreMatchers.is(CoreMatchers.notNullValue()));

        List<SpanDataWrapper> root = spans.byDepth().get(0);
        attributes = root.get(0).spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.CI_PIPELINE_TYPE), CoreMatchers.is(OtelUtils.WORKFLOW));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.CI_PIPELINE_MULTIBRANCH_TYPE), CoreMatchers.nullValue());
    }

    @Test
    public void testFreestyleJob() throws Exception {
        final String jobName = "test-freestyle-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);

        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", jobName);
        checkChainOfSpans(spans, "Phase: Run");
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(4L));

        List<SpanDataWrapper> root = spans.byDepth().get(0);
        Attributes attributes = root.get(0).spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.CI_PIPELINE_TYPE), CoreMatchers.is(OtelUtils.FREESTYLE));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.CI_PIPELINE_MULTIBRANCH_TYPE), CoreMatchers.nullValue());

        // Environment variables are populated
        EnvVars environment = build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO));
        assertEnvironmentVariables(environment);
    }

    @Test
    public void testPipelineWithSkippedSteps() throws Exception {
        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
                "node() {\n" +
                "    stage('ze-stage1') {\n" +
                "       xsh (label: 'shell-1', script: 'echo ze-echo-1') \n" +
                "       echo 'ze-echo-step' \n" +
                "    }\n" +
                "    stage('ze-stage2') {\n" +
                "       xsh (label: 'shell-2', script: 'echo ze-echo-2') \n" +
                "    }\n" +
                "}";

        final Node agent = jenkinsRule.createOnlineSlave();

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-pipeline-with-skipped-tests-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "shell-1", "Stage: ze-stage1", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        checkChainOfSpans(spans, "shell-2", "Stage: ze-stage2", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(10L));

        Optional<Tree.Node<SpanDataWrapper>> stageNode = spans.breadthFirstSearchNodes(node -> "Stage: ze-stage1".equals(node.getData().spanData.getName()));
        MatcherAssert.assertThat(stageNode, CoreMatchers.is(CoreMatchers.notNullValue()));

        Attributes attributes = stageNode.get().getData().spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION), CoreMatchers.is(CoreMatchers.notNullValue()));
    }

    @Test
    public void testPipelineWithWrappingStep() throws Exception {
        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
                "node() {\n" +
                "    stage('ze-stage1') {\n" +
                "       withEnv(['MY_VARIABLE=MY_VALUE']) {\n" +
                "          xsh (label: 'shell-1', script: 'echo ze-echo-1') \n" +
                "       }\n" +
                "       xsh 'echo ze-echo' \n" +
                "    }\n" +
                "    stage('ze-stage2') {\n" +
                "       xsh (label: 'shell-2', script: 'echo ze-echo-2') \n" +
                "    }\n" +
                "}";
        final Node agent = jenkinsRule.createOnlineSlave();

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-pipeline-with-wrapping-step-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "shell-1", "Stage: ze-stage1", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        checkChainOfSpans(spans, "shell-2", "Stage: ze-stage2", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(11L));

        Optional<Tree.Node<SpanDataWrapper>> shellNode = spans.breadthFirstSearchNodes(node -> "shell-1".equals(node.getData().spanData.getName()));
        MatcherAssert.assertThat(shellNode, CoreMatchers.is(CoreMatchers.notNullValue()));

        Attributes attributes = shellNode.get().getData().spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION), CoreMatchers.is(CoreMatchers.notNullValue()));
    }

    @Test
    public void testPipelineWithError() throws Exception {
        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
                "node() {\n" +
                "    stage('ze-stage1') {\n" +
                "       xsh (label: 'shell-1', script: 'echo ze-echo-1') \n" +
                "    }\n" +
                "    stage('ze-stage2') {\n" +
                "       xsh (label: 'shell-2', script: 'echo ze-echo-2') \n" +
                "       error 'ze-pipeline-error' \n" +
                "    }\n" +
                "}";
        final Node node = jenkinsRule.createOnlineSlave();

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-pipeline-with-error-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "shell-1", "Stage: ze-stage1", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        checkChainOfSpans(spans, "shell-2", "Stage: ze-stage2", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        checkChainOfSpans(spans, "error", "Stage: ze-stage2", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(11L));
    }

    @Test
    public void testPipelineWithParallelStep() throws Exception {
        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
                "node() {\n" +
                "    stage('ze-parallel-stage') {\n" +
                "        parallel parallelBranch1: {\n" +
                "            xsh (label: 'shell-1', script: 'echo this-is-the-parallel-branch-1')\n" +
                "        } ,parallelBranch2: {\n" +
                "            xsh (label: 'shell-2', script: 'echo this-is-the-parallel-branch-2')\n" +
                "        } ,parallelBranch3: {\n" +
                "            xsh (label: 'shell-3', script: 'echo this-is-the-parallel-branch-3')\n" +
                "        }\n" +
                "    }\n" +
                "}";
        final Node agent = jenkinsRule.createOnlineSlave();

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-pipeline-with-parallel-step" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "shell-1", "Parallel branch: parallelBranch1", "Stage: ze-parallel-stage", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        checkChainOfSpans(spans, "shell-2", "Parallel branch: parallelBranch2", "Stage: ze-parallel-stage", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        checkChainOfSpans(spans, "shell-3", "Parallel branch: parallelBranch3", "Stage: ze-parallel-stage", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(13L));

        Optional<Tree.Node<SpanDataWrapper>> branchNode = spans.breadthFirstSearchNodes(node -> "Parallel branch: parallelBranch1".equals(node.getData().spanData.getName()));
        MatcherAssert.assertThat(branchNode, CoreMatchers.is(CoreMatchers.notNullValue()));

        Attributes attributes = branchNode.get().getData().spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION), CoreMatchers.is(CoreMatchers.notNullValue()));
    }

    @Test
    public void testPipelineWithGitCredentialsSteps() throws Exception {
        // Details defined in the JCasC file -> io/jenkins/plugins/opentelemetry/jcasc-elastic-backend.yml
        final String userName = "my-user-2";
        final String globalCredentialId = "user-and-password";
        final String jobName = "git-" + jobNameSuffix.incrementAndGet();

        // Then the git username should be the one set in the credentials.
        assertGitCredentials(jobName, globalCredentialId, userName);
    }

    @Test
    public void testPipelineWithSshCredentialsSteps() throws Exception {
        // Details defined in the JCasC file -> io/jenkins/plugins/opentelemetry/jcasc-elastic-backend.yml
        final String userName = "my-user-1";
        final String globalCredentialId = "ssh-private-key";
        final String jobName = "ssh-" + jobNameSuffix.incrementAndGet();

        // Then the git username should be the one set in the credentials.
        assertGitCredentials(jobName, globalCredentialId, userName);
    }

    @Test
    public void testPipelineWithoutGitCredentialsSteps() throws Exception {
        String credentialId = "unknown";
        final String jobName = "cred-" + jobNameSuffix.incrementAndGet();

        // Then the git username should be the credentialsId since there is no entry in the credentials provider.
        assertGitCredentials(jobName, credentialId, credentialId);
    }

    private void assertGitCredentials(String jobName, String globalCredentialId, String gitUserName) throws Exception {
        String pipelineScript = "node() {\n" +
                "  stage('foo') {\n" +
                "    git credentialsId: '" + globalCredentialId + "', url: 'https://github.com/jenkinsci/opentelemetry-plugin' \n" +
                "  }\n" +
                "}";
        final Node agent = jenkinsRule.createOnlineSlave();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "git: github.com/jenkinsci/opentelemetry-plugin", "Stage: foo", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(8L));

        Optional<Tree.Node<SpanDataWrapper>> gitNode = spans.breadthFirstSearchNodes(node -> "git: github.com/jenkinsci/opentelemetry-plugin".equals(node.getData().spanData.getName()));
        MatcherAssert.assertThat(gitNode, CoreMatchers.is(CoreMatchers.notNullValue()));

        Attributes attributes = gitNode.get().getData().spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_USERNAME), CoreMatchers.is(gitUserName));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION), CoreMatchers.is(CoreMatchers.notNullValue()));
    }

    @Test
    public void testPipelineWithCheckoutShallowSteps() throws Exception {
        final String jobName = "co-" + jobNameSuffix.incrementAndGet();

        String pipelineScript = "node() {\n" +
            "  stage('foo') {\n" +
            "    checkout([$class: 'GitSCM', branches: [[name: '*/master']], extensions: [[$class: 'CloneOption', depth: 2, noTags: true, reference: '', shallow: true]], userRemoteConfigs: [[url: 'https://github.com/jenkinsci/opentelemetry-plugin']]]) \n" +
            "  }\n" +
            "}";
        final Node agent = jenkinsRule.createOnlineSlave();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "checkout: github.com/jenkinsci/opentelemetry-plugin", "Stage: foo", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(8L));

        Optional<Tree.Node<SpanDataWrapper>> checkoutNode = spans.breadthFirstSearchNodes(node -> "checkout: github.com/jenkinsci/opentelemetry-plugin".equals(node.getData().spanData.getName()));
        MatcherAssert.assertThat(checkoutNode, CoreMatchers.is(CoreMatchers.notNullValue()));

        Attributes attributes = checkoutNode.get().getData().spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_CLONE_SHALLOW), CoreMatchers.is(true));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_CLONE_DEPTH), CoreMatchers.is(2L));
    }

}
