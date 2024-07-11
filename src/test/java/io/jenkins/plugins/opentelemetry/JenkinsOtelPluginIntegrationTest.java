/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.junit.Assume.assumeFalse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.ExtensionList;
import io.jenkins.plugins.opentelemetry.job.step.SpanContextPropagationSynchronousTestStep;
import org.apache.commons.lang3.SystemUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.EchoStep;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.recipes.WithPlugin;

import com.github.rutledgepaulv.prune.Tree;
import com.google.common.collect.Iterables;

import hudson.Functions;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporterUtils;
import io.opentelemetry.sdk.trace.data.SpanData;

/**
 * Note usage of `def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}}` is inspired by
 * https://github.com/jenkinsci/workflow-basic-steps-plugin/blob/474cea2a53753e1fb9b166fa1ca0f6184b5cee4a/src/test/java/org/jenkinsci/plugins/workflow/steps/IsUnixStepTest.java#L39
 */
public class JenkinsOtelPluginIntegrationTest extends BaseIntegrationTest {
    private static final Logger LOGGER = Logger.getLogger(Run.class.getName());

    @Test
    public void testSimplePipeline() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
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
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-simple-pipeline-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();

        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI, JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "shell-1", "Stage: ze-stage1", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "shell-2", "Stage: ze-stage2", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(10L));

        // FIXME REPAIR METRICS TESTS
        /*
        // WORKAROUND because we don't know how to force the IntervalMetricReader to collect metrics
        jenkinsControllerOpenTelemetry.getOpenTelemetrySdk().getSdkMeterProvider().forceFlush();
        Map<String, MetricData> exportedMetrics = InMemoryMetricExporterUtils.getLastExportedMetricByMetricName(InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE.getFinishedMetricItems());
        dumpMetrics(exportedMetrics);
        MetricData runStartedCounterData = exportedMetrics.get(JenkinsSemanticMetrics.CI_PIPELINE_RUN_STARTED);
        MatcherAssert.assertThat(runStartedCounterData, CoreMatchers.notNullValue());
        // TODO TEST METRICS WITH PROPER RESET BETWEEN TESTS
        MatcherAssert.assertThat(runStartedCounterData.getType(), CoreMatchers.is(MetricDataType.LONG_SUM));
        Collection<LongPointData> metricPoints = runStartedCounterData.getLongSumData().getPoints();
        //MatcherAssert.assertThat(Iterables.getLast(metricPoints).getValue(), CoreMatchers.is(1L));
        // we dont test the metric CI_PIPELINE_RUN_COMPLETED because there is flakiness on it
        */
    }

    @Ignore("Lifecycle problem, the InMemoryMetricExporter gets reset too much and the disk usage is not captured")
    @Test
    @WithPlugin("cloudbees-disk-usage-simple")
    public void testMetricsWithDiskUsagePlugin() throws Exception {
        LOGGER.log(Level.INFO, "testMetricsWithDiskUsagePlugin...");
        // WORKAROUND because we don't know how to force the IntervalMetricReader to collect metrics
        Thread.sleep(100); // FIXME
        LOGGER.log(Level.INFO, "slept");

        jenkinsControllerOpenTelemetry.getOpenTelemetrySdk().getSdkMeterProvider().forceFlush();

        LOGGER.log(Level.INFO, "InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE: " + InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE);
        Map<String, MetricData> exportedMetrics = InMemoryMetricExporterUtils.getLastExportedMetricByMetricName(InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE.getFinishedMetricItems());
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
            jenkinsRule.createOnlineSlave();

            WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-trace-environment-variables-injected-in-shell-steps-" + jobNameSuffix.incrementAndGet());
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

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
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(spans, JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI, JenkinsOtelSemanticAttributes.AGENT_UI, "Stage: foo", "Phase: Run");
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(7L));

        Optional<Tree.Node<SpanDataWrapper>> executorNodeAllocation = spans.breadthFirstSearchNodes(node -> (JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI).equals(node.getData().spanData.getName()));
        MatcherAssert.assertThat(executorNodeAllocation, CoreMatchers.is(CoreMatchers.notNullValue()));

        Attributes attributes = executorNodeAllocation.get().getData().spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_AGENT_LABEL), CoreMatchers.is("linux"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION), CoreMatchers.is(CoreMatchers.notNullValue()));

        Optional<Tree.Node<SpanDataWrapper>> executorNode = spans.breadthFirstSearchNodes(node -> (JenkinsOtelSemanticAttributes.AGENT_UI).equals(node.getData().spanData.getName()));
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
    public void testPipelineWithSkippedSteps() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
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

        jenkinsRule.createOnlineSlave();

        String jobName = "test-pipeline-with-skipped-tests-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "shell-1", "Stage: ze-stage1", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "shell-2", "Stage: ze-stage2", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run", rootSpanName);
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
        jenkinsRule.createOnlineSlave();

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-pipeline-with-wrapping-step-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

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
        jenkinsRule.createOnlineSlave();

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-pipeline-with-error-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "shell-1", "Stage: ze-stage1", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        checkChainOfSpans(spans, "shell-2", "Stage: ze-stage2", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        checkChainOfSpans(spans, "error", "Stage: ze-stage2", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(11L));
    }

    @Test
    public void testChainOfPipelines() throws Exception {
        jenkinsRule.createOnlineSlave();

        String childPipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "node() {\n" +
            "    stage('child-pipeline') {\n" +
            "       echo 'child-pipeline' \n" +
            "    }\n" +
            "}";
        WorkflowJob childPipeline = jenkinsRule.createProject(WorkflowJob.class, "child-pipeline-" + jobNameSuffix.incrementAndGet());
        childPipeline.setDefinition(new CpsFlowDefinition(childPipelineScript, true));

        String parentPipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "node() {\n" +
            "    stage('trigger-child-pipeline') {\n" +
            "       build '" + childPipeline.getName() + "' \n" +
            "    }\n" +
            "}";

        WorkflowJob parentPipeline = jenkinsRule.createProject(WorkflowJob.class, "parent-pipeline-" + jobNameSuffix.incrementAndGet());
        parentPipeline.setDefinition(new CpsFlowDefinition(parentPipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, parentPipeline.scheduleBuild2(0));

        String childPipelineRootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + childPipeline.getName();

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans,
            "Stage: child-pipeline",
            JenkinsOtelSemanticAttributes.AGENT_UI,
            "Phase: Run",
            childPipelineRootSpanName, // child pipeline execution
            "build: " + childPipeline.getName(),
            "Stage: trigger-child-pipeline",
            JenkinsOtelSemanticAttributes.AGENT_UI,
            "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(15L));
    }

    @Test
    public void testPipelineWithParallelStep() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
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
        jenkinsRule.createOnlineSlave();

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-pipeline-with-parallel-step" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

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
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // Details defined in the JCasC file -> io/jenkins/plugins/opentelemetry/jcasc-elastic-backend.yml
        final String userName = "my-user-2";
        final String globalCredentialId = "user-and-password";
        final String jobName = "git-credentials-" + jobNameSuffix.incrementAndGet();

        // Then the git username should be the one set in the credentials.
        assertGitCredentials(jobName, globalCredentialId, userName);
    }

    @Test
    public void testPipelineWithSshCredentialsSteps() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // Details defined in the JCasC file -> io/jenkins/plugins/opentelemetry/jcasc-elastic-backend.yml
        final String userName = "my-user-1";
        final String globalCredentialId = "ssh-private-key";
        final String jobName = "ssh-credentials-" + jobNameSuffix.incrementAndGet();

        // Then the git username should be the one set in the credentials.
        assertGitCredentials(jobName, globalCredentialId, userName);
    }

    @Test
    public void testPipelineWithoutGitCredentialsSteps() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        String credentialId = "unknown";
        final String jobName = "git-credentials-" + jobNameSuffix.incrementAndGet();

        // Then the git username should be the credentialsId since there is no entry in the credentials provider.
        assertGitCredentials(jobName, credentialId, credentialId);
    }

    private void assertGitCredentials(String jobName, String globalCredentialId, String gitUserName) throws Exception {
        String pipelineScript = "node() {\n" +
                "  stage('foo') {\n" +
                "    git credentialsId: '" + globalCredentialId + "', url: 'https://github.com/octocat/Hello-World' \n" +
                "  }\n" +
                "}";
        jenkinsRule.createOnlineSlave();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "git: github.com/octocat/Hello-World", "Stage: foo", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(8L));

        Optional<Tree.Node<SpanDataWrapper>> gitNode = spans.breadthFirstSearchNodes(node -> "git: github.com/octocat/Hello-World".equals(node.getData().spanData.getName()));
        MatcherAssert.assertThat(gitNode, CoreMatchers.is(CoreMatchers.notNullValue()));

        Attributes attributes = gitNode.get().getData().spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_USERNAME), CoreMatchers.is(gitUserName));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION), CoreMatchers.is(CoreMatchers.notNullValue()));
    }

    @Test
    public void testPipelineWithCheckoutShallowSteps() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "with-checkout-" + jobNameSuffix.incrementAndGet();

        String pipelineScript = "node() {\n" +
            "  stage('foo') {\n" +
            "    checkout([$class: 'GitSCM', branches: [[name: '*/master']], extensions: [[$class: 'CloneOption', depth: 2, noTags: true, reference: '', shallow: true]], userRemoteConfigs: [[url: 'https://github.com/octocat/Hello-World']]]) \n" +
            "  }\n" +
            "}";
        jenkinsRule.createOnlineSlave();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "checkout: github.com/octocat/Hello-World", "Stage: foo", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(8L));

        Optional<Tree.Node<SpanDataWrapper>> checkoutNode = spans.breadthFirstSearchNodes(node -> "checkout: github.com/octocat/Hello-World".equals(node.getData().spanData.getName()));
        MatcherAssert.assertThat(checkoutNode, CoreMatchers.is(CoreMatchers.notNullValue()));

        Attributes attributes = checkoutNode.get().getData().spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_CLONE_SHALLOW), CoreMatchers.is(true));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_CLONE_DEPTH), CoreMatchers.is(2L));
    }

    @Test
    public void testPipelineWithoutCheckoutShallowSteps() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "without-checkout-" + jobNameSuffix.incrementAndGet();

        String pipelineScript = "node() {\n" +
            "  stage('foo') {\n" +
            "    checkout([$class: 'GitSCM', branches: [[name: '*/master']], extensions: [], userRemoteConfigs: [[url: 'https://github.com/octocat/Hello-World']]]) \n" +
            "  }\n" +
            "}";
        jenkinsRule.createOnlineSlave();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "checkout: github.com/octocat/Hello-World", "Stage: foo", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(8L));

        Optional<Tree.Node<SpanDataWrapper>> checkoutNode = spans.breadthFirstSearchNodes(node -> "checkout: github.com/octocat/Hello-World".equals(node.getData().spanData.getName()));
        Attributes attributes = checkoutNode.get().getData().spanData.getAttributes();

        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_CLONE_SHALLOW), CoreMatchers.is(false));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_CLONE_DEPTH), CoreMatchers.is(0L));
    }

    @Test
    public void testFailFastParallelScriptedPipelineWithException() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        String jobName = "fail-fast-parallel-scripted-pipeline-with-failure" + jobNameSuffix.incrementAndGet();

        String pipelineScript = "node() {\n" +
            "    stage('ze-parallel-stage') {\n" +
            "        parallel failingBranch: {\n" +
            "            error 'the failure that will cause the interruption of other branches'\n" +
            "        }, branchThatWillBeInterrupted: {\n" +
            "            sleep 5\n" +
            "        }, failFast:true\n" +
            "    }\n" +
            "}";
        jenkinsRule.createOnlineSlave();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "sleep", "Parallel branch: branchThatWillBeInterrupted", "Stage: ze-parallel-stage", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");

        SpanData sleepSpanData = spans.breadthFirstSearchNodes(node -> "sleep".equals(node.getData().spanData.getName())).get().getData().spanData;
        MatcherAssert.assertThat(sleepSpanData.getStatus().getStatusCode(), CoreMatchers.is(StatusCode.UNSET));

        SpanData branchThatWillBeInterruptedSpanData = spans.breadthFirstSearchNodes(node -> "Parallel branch: branchThatWillBeInterrupted".equals(node.getData().spanData.getName())).get().getData().spanData;
        MatcherAssert.assertThat(branchThatWillBeInterruptedSpanData.getStatus().getStatusCode(), CoreMatchers.is(StatusCode.UNSET));
        MatcherAssert.assertThat(branchThatWillBeInterruptedSpanData.getStatus().getDescription(), CoreMatchers.is("FlowInterruptedException: FailFastCause: Failed in branch failingBranch"));
        MatcherAssert.assertThat(branchThatWillBeInterruptedSpanData.getAttributes().get(JenkinsOtelSemanticAttributes.JENKINS_STEP_INTERRUPTION_CAUSES), CoreMatchers.is(List.of("FailFastCause: Failed in branch failingBranch")));

        SpanData failingBranchSpanData = spans.breadthFirstSearchNodes(node -> "Parallel branch: failingBranch".equals(node.getData().spanData.getName())).get().getData().spanData;
        MatcherAssert.assertThat(failingBranchSpanData.getStatus().getStatusCode(), CoreMatchers.is(StatusCode.ERROR));
        MatcherAssert.assertThat(failingBranchSpanData.getStatus().getDescription(), CoreMatchers.is("the failure that will cause the interruption of other branches"));
    }

    @Test
    public void testSpanContextPropagationSynchronousTestStep() throws Exception {
        Set.of(EchoStep.class, EchoStep.DescriptorImpl.class, SpanContextPropagationSynchronousTestStep.class).forEach(c -> System.out.println(c + " -> " +ExtensionList.lookup(c)));


        String pipelineScript =
            "node() {\n" +
                "    stage('ze-stage1') {\n" +
                "       echo message: 'hello'\n" +
                "       spanContextPropagationSynchronousTestStep()\n" +
                "    }\n" +
                "}";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-SpanContextPropagationSynchronousTestStep-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans,"SpanContextPropagationTestStep.execution", "spanContextPropagationSynchronousTestStep", "Stage: ze-stage1", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
    }
    @Test
    public void testSpanContextPropagationSynchronousNonBlockingTestStep() throws Exception {
        Set.of(EchoStep.class, EchoStep.DescriptorImpl.class, SpanContextPropagationSynchronousTestStep.class).forEach(c -> System.out.println(c + " -> " +ExtensionList.lookup(c)));


        String pipelineScript =
            "node() {\n" +
                "    stage('ze-stage1') {\n" +
                "       echo message: 'hello'\n" +
                "       spanContextPropagationSynchronousNonBlockingTestStep()\n" +
                "    }\n" +
                "}";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-SpanContextPropagationSynchronousTestStep-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans,"SpanContextPropagationSynchronousNonBlockingTestStep.execution", "spanContextPropagationSynchronousNonBlockingTestStep", "Stage: ze-stage1", JenkinsOtelSemanticAttributes.AGENT_UI, "Phase: Run");
    }

}
