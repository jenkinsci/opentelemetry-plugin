/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.github.rutledgepaulv.prune.Tree;
import com.google.common.collect.Iterables;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.job.step.SpanContextPropagationSynchronousTestStep;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsMetrics;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporterUtils;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.EchoStep;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.recipes.WithPlugin;

/**
 * Note usage of `def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}}` is inspired by
 * https://github.com/jenkinsci/workflow-basic-steps-plugin/blob/474cea2a53753e1fb9b166fa1ca0f6184b5cee4a/src/test/java/org/jenkinsci/plugins/workflow/steps/IsUnixStepTest.java#L39
 */
class JenkinsOtelPluginIntegrationTest extends BaseIntegrationTest {

    private static final Logger LOGGER = Logger.getLogger(Run.class.getName());

    @Test
    void testSimplePipeline() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // BEFORE

        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            node() {
                stage('ze-stage1') {
                   xsh (label: 'shell-1', script: 'echo ze-echo-1')\s
                }
                stage('ze-stage2') {
                   xsh (label: 'shell-2', script: 'echo ze-echo-2')\s
                }
            }""";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-simple-pipeline-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getBuildTrace();

        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(
                spans,
                ExtendedJenkinsAttributes.AGENT_ALLOCATION_UI,
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run",
                rootSpanName);
        checkChainOfSpans(
                spans, "shell-1", "Stage: ze-stage1", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(
                spans, "shell-2", "Stage: ze-stage2", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);
        assertThat(spans.cardinality(), is(10L));

        // FIXME REPAIR METRICS TESTS
        /*
        // WORKAROUND because we don't know how to force the IntervalMetricReader to collect metrics
        jenkinsControllerOpenTelemetry.getOpenTelemetrySdk().getSdkMeterProvider().forceFlush();
        Map<String, MetricData> exportedMetrics = InMemoryMetricExporterUtils.getLastExportedMetricByMetricName(InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE.getFinishedMetricItems());
        dumpMetrics(exportedMetrics);
        MetricData runStartedCounterData = exportedMetrics.get(JenkinsSemanticMetrics.CI_PIPELINE_RUN_STARTED);
        assertThat(runStartedCounterData, notNullValue());
        // TODO TEST METRICS WITH PROPER RESET BETWEEN TESTS
        assertThat(runStartedCounterData.getType(), is(MetricDataType.LONG_SUM));
        Collection<LongPointData> metricPoints = runStartedCounterData.getLongSumData().getPoints();
        //MatcherAssert.assertThat(Iterables.getLast(metricPoints).getValue(), is(1L));
        // we dont test the metric CI_PIPELINE_RUN_COMPLETED because there is flakiness on it
        */
    }

    @Disabled("Lifecycle problem, the InMemoryMetricExporter gets reset too much and the disk usage is not captured")
    @Test
    @WithPlugin("cloudbees-disk-usage-simple")
    void testMetricsWithDiskUsagePlugin() throws Exception {
        LOGGER.log(Level.INFO, "testMetricsWithDiskUsagePlugin...");
        // WORKAROUND because we don't know how to force the IntervalMetricReader to collect metrics
        Thread.sleep(100); // FIXME
        LOGGER.log(Level.INFO, "slept");

        jenkinsControllerOpenTelemetry
                .getOpenTelemetrySdk()
                .getSdkMeterProvider()
                .forceFlush();

        LOGGER.log(
                Level.INFO,
                "InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE: "
                        + InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE);
        Map<String, MetricData> exportedMetrics = InMemoryMetricExporterUtils.getLastExportedMetricByMetricName(
                InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE.getFinishedMetricItems());
        dumpMetrics(exportedMetrics);
        MetricData diskUsageData = exportedMetrics.get(JenkinsMetrics.JENKINS_DISK_USAGE_BYTES);
        assertThat(diskUsageData, notNullValue());
        // TODO TEST METRICS WITH PROPER RESET BETWEEN TESTS
        assertThat(diskUsageData.getType(), is(MetricDataType.LONG_GAUGE));
        Collection<LongPointData> metricPoints =
                diskUsageData.getLongGaugeData().getPoints();
        assertThat(Iterables.getLast(metricPoints).getValue(), notNullValue());
    }

    @Test
    void testTraceEnvironmentVariablesInjectedInShellSteps() throws Exception {
        if (Functions.isWindows()) {
            // TODO test on windows
        } else {
            String pipelineScript = """
                node() {
                    stage('ze-stage1') {
                       sh '''
                if [ -z $TRACEPARENT ]
                then
                   echo TRACEPARENT NOT FOUND
                   exit 1
                fi
                '''
                    }
                }""";
            jenkinsRule.createOnlineSlave();

            WorkflowJob pipeline = jenkinsRule.createProject(
                    WorkflowJob.class,
                    "test-trace-environment-variables-injected-in-shell-steps-" + jobNameSuffix.incrementAndGet());
            pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
            jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

            final Tree<SpanDataWrapper> spans = getBuildTrace();
            assertThat(spans.cardinality(), is(8L));
        }
    }

    @Test
    void testPipelineWithNodeSteps() throws Exception {
        String pipelineScript = """
            pipeline {
              agent none
              stages {
                stage('foo') {
                  steps {
                    node('linux') {\s
                      echo 'hello world'\s
                    }
                  }
                }
              }
            }""";

        final Node agent = jenkinsRule.createOnlineSlave();
        agent.setLabelString("linux");

        final String jobName = "test-pipeline-with-node-steps-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(spans, "Phase: Start", rootSpanName);
        checkChainOfSpans(
                spans,
                ExtendedJenkinsAttributes.AGENT_ALLOCATION_UI,
                ExtendedJenkinsAttributes.AGENT_UI,
                "Stage: foo",
                "Phase: Run");
        checkChainOfSpans(spans, "Phase: Finalise", rootSpanName);
        assertThat(spans.cardinality(), is(7L));

        Optional<Tree.Node<SpanDataWrapper>> executorNodeAllocation =
                spans.breadthFirstSearchNodes(node -> (ExtendedJenkinsAttributes.AGENT_ALLOCATION_UI)
                        .equals(node.getData().spanData().getName()));
        assertThat(executorNodeAllocation, is(notNullValue()));

        Attributes attributes = executorNodeAllocation.get().getData().spanData().getAttributes();
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_AGENT_LABEL), is("linux"));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME),
                is(notNullValue()));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION),
                is(notNullValue()));

        Optional<Tree.Node<SpanDataWrapper>> executorNode =
                spans.breadthFirstSearchNodes(node -> (ExtendedJenkinsAttributes.AGENT_UI)
                        .equals(node.getData().spanData().getName()));
        assertThat(executorNode, is(notNullValue()));
        attributes = executorNode.get().getData().spanData().getAttributes();
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_AGENT_LABEL), is("linux"));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME),
                is(notNullValue()));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION),
                is(notNullValue()));

        List<SpanDataWrapper> root = spans.byDepth().get(0);
        attributes = root.get(0).spanData().getAttributes();
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.CI_PIPELINE_TYPE), is(OtelUtils.WORKFLOW));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.CI_PIPELINE_MULTIBRANCH_TYPE), nullValue());
    }

    @Test
    void testPipelineWithSkippedSteps() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            node() {
                stage('ze-stage1') {
                   xsh (label: 'shell-1', script: 'echo ze-echo-1')\s
                   echo 'ze-echo-step'\s
                }
                stage('ze-stage2') {
                   xsh (label: 'shell-2', script: 'echo ze-echo-2')\s
                }
            }""";

        jenkinsRule.createOnlineSlave();

        String jobName = "test-pipeline-with-skipped-tests-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(
                spans, "shell-1", "Stage: ze-stage1", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        checkChainOfSpans(
                spans, "shell-2", "Stage: ze-stage2", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run", rootSpanName);
        assertThat(spans.cardinality(), is(10L));

        Optional<Tree.Node<SpanDataWrapper>> stageNode = spans.breadthFirstSearchNodes(
                node -> "Stage: ze-stage1".equals(node.getData().spanData().getName()));
        assertThat(stageNode, is(notNullValue()));

        Attributes attributes = stageNode.get().getData().spanData().getAttributes();
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME),
                is(notNullValue()));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION),
                is(notNullValue()));
    }

    @Test
    void testPipelineWithWrappingStep() throws Exception {
        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            node() {
                stage('ze-stage1') {
                   withEnv(['MY_VARIABLE=MY_VALUE']) {
                      xsh (label: 'shell-1', script: 'echo ze-echo-1')\s
                   }
                   xsh 'echo ze-echo'\s
                }
                stage('ze-stage2') {
                   xsh (label: 'shell-2', script: 'echo ze-echo-2')\s
                }
            }""";
        jenkinsRule.createOnlineSlave();

        WorkflowJob pipeline = jenkinsRule.createProject(
                WorkflowJob.class, "test-pipeline-with-wrapping-step-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(spans, "shell-1", "Stage: ze-stage1", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run");
        checkChainOfSpans(spans, "shell-2", "Stage: ze-stage2", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run");
        assertThat(spans.cardinality(), is(11L));

        Optional<Tree.Node<SpanDataWrapper>> shellNode = spans.breadthFirstSearchNodes(
                node -> "shell-1".equals(node.getData().spanData().getName()));
        assertThat(shellNode, is(notNullValue()));

        Attributes attributes = shellNode.get().getData().spanData().getAttributes();
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME),
                is(notNullValue()));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION),
                is(notNullValue()));
    }

    @Test
    void testPipelineWithError() throws Exception {
        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            node() {
                stage('ze-stage1') {
                   xsh (label: 'shell-1', script: 'echo ze-echo-1')\s
                }
                stage('ze-stage2') {
                   xsh (label: 'shell-2', script: 'echo ze-echo-2')\s
                   error 'ze-pipeline-error'\s
                }
            }""";
        jenkinsRule.createOnlineSlave();

        WorkflowJob pipeline = jenkinsRule.createProject(
                WorkflowJob.class, "test-pipeline-with-error-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(spans, "shell-1", "Stage: ze-stage1", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run");
        checkChainOfSpans(spans, "shell-2", "Stage: ze-stage2", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run");
        checkChainOfSpans(spans, "error", "Stage: ze-stage2", ExtendedJenkinsAttributes.AGENT_UI, "Phase: Run");
        assertThat(spans.cardinality(), is(11L));
    }

    @Test
    @Timeout(300)
    void testChainOfPipelines() throws Exception {
        jenkinsRule.createOnlineSlave();

        String childPipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            node() {
                stage('child-pipeline') {
                   echo 'child-pipeline'\s
                }
            }""";
        WorkflowJob childPipeline =
                jenkinsRule.createProject(WorkflowJob.class, "child-pipeline-" + jobNameSuffix.incrementAndGet());
        childPipeline.setDefinition(new CpsFlowDefinition(childPipelineScript, true));

        String parentPipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" + "node() {\n"
                + "    stage('trigger-child-pipeline') {\n"
                + "       build '"
                + childPipeline.getName() + "' \n" + "    }\n"
                + "}";

        WorkflowJob parentPipeline =
                jenkinsRule.createProject(WorkflowJob.class, "parent-pipeline-" + jobNameSuffix.incrementAndGet());
        parentPipeline.setDefinition(new CpsFlowDefinition(parentPipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, parentPipeline.scheduleBuild2(0));

        String childPipelineRootSpanName =
                ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + childPipeline.getName();

        final Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(
                spans,
                "Stage: child-pipeline",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run",
                childPipelineRootSpanName, // child pipeline execution
                "build: " + childPipeline.getName(),
                "Stage: trigger-child-pipeline",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run");
        assertThat(spans.cardinality(), is(15L));
    }

    @Test
    void testPipelineWithParallelStep() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            node() {
                stage('ze-parallel-stage') {
                    parallel parallelBranch1: {
                        xsh (label: 'shell-1', script: 'echo this-is-the-parallel-branch-1')
                    } ,parallelBranch2: {
                        xsh (label: 'shell-2', script: 'echo this-is-the-parallel-branch-2')
                    } ,parallelBranch3: {
                        xsh (label: 'shell-3', script: 'echo this-is-the-parallel-branch-3')
                    }
                }
            }""";
        jenkinsRule.createOnlineSlave();

        WorkflowJob pipeline = jenkinsRule.createProject(
                WorkflowJob.class, "test-pipeline-with-parallel-step" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(
                spans,
                "shell-1",
                "Parallel branch: parallelBranch1",
                "Stage: ze-parallel-stage",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run");
        checkChainOfSpans(
                spans,
                "shell-2",
                "Parallel branch: parallelBranch2",
                "Stage: ze-parallel-stage",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run");
        checkChainOfSpans(
                spans,
                "shell-3",
                "Parallel branch: parallelBranch3",
                "Stage: ze-parallel-stage",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run");
        assertThat(spans.cardinality(), is(13L));

        Optional<Tree.Node<SpanDataWrapper>> branchNode =
                spans.breadthFirstSearchNodes(node -> "Parallel branch: parallelBranch1"
                        .equals(node.getData().spanData().getName()));
        assertThat(branchNode, is(notNullValue()));

        Attributes attributes = branchNode.get().getData().spanData().getAttributes();
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME),
                is(notNullValue()));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION),
                is(notNullValue()));
    }

    @Test
    void testPipelineWithGitCredentialsSteps() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // Details defined in the JCasC file -> io/jenkins/plugins/opentelemetry/jcasc-elastic-backend.yml
        final String userName = "my-user-2";
        final String globalCredentialId = "user-and-password";
        final String jobName = "git-credentials-" + jobNameSuffix.incrementAndGet();

        // Then the git username should be the one set in the credentials.
        assertGitCredentials(jobName, globalCredentialId, userName);
    }

    @Test
    void testPipelineWithSshCredentialsSteps() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        // Details defined in the JCasC file -> io/jenkins/plugins/opentelemetry/jcasc-elastic-backend.yml
        final String userName = "my-user-1";
        final String globalCredentialId = "ssh-private-key";
        final String jobName = "ssh-credentials-" + jobNameSuffix.incrementAndGet();

        // Then the git username should be the one set in the credentials.
        assertGitCredentials(jobName, globalCredentialId, userName);
    }

    @Test
    void testPipelineWithoutGitCredentialsSteps() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        String credentialId = "unknown";
        final String jobName = "git-credentials-" + jobNameSuffix.incrementAndGet();

        // Then the git username should be the credentialsId since there is no entry in the credentials provider.
        assertGitCredentials(jobName, credentialId, credentialId);
    }

    private void assertGitCredentials(String jobName, String globalCredentialId, String gitUserName) throws Exception {
        String pipelineScript = "node() {\n" + "  stage('foo') {\n"
                + "    git credentialsId: '"
                + globalCredentialId + "', url: 'https://github.com/octocat/Hello-World' \n" + "  }\n"
                + "}";
        jenkinsRule.createOnlineSlave();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(
                spans,
                "git: github.com/octocat/Hello-World",
                "Stage: foo",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run");
        assertThat(spans.cardinality(), is(8L));

        Optional<Tree.Node<SpanDataWrapper>> gitNode =
                spans.breadthFirstSearchNodes(node -> "git: github.com/octocat/Hello-World"
                        .equals(node.getData().spanData().getName()));
        assertThat(gitNode, is(notNullValue()));

        Attributes attributes = gitNode.get().getData().spanData().getAttributes();
        assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_USERNAME), is(gitUserName));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME),
                is(notNullValue()));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION),
                is(notNullValue()));
    }

    @Test
    void testPipelineWithCheckoutShallowSteps() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "with-checkout-" + jobNameSuffix.incrementAndGet();

        String pipelineScript = """
            node() {
              stage('foo') {
                checkout([$class: 'GitSCM', branches: [[name: '*/master']], extensions: [[$class: 'CloneOption', depth: 2, noTags: true, reference: '', shallow: true]], userRemoteConfigs: [[url: 'https://github.com/octocat/Hello-World']]])\s
              }
            }""";
        jenkinsRule.createOnlineSlave();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(
                spans,
                "checkout: github.com/octocat/Hello-World",
                "Stage: foo",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run");
        assertThat(spans.cardinality(), is(8L));

        Optional<Tree.Node<SpanDataWrapper>> checkoutNode =
                spans.breadthFirstSearchNodes(node -> "checkout: github.com/octocat/Hello-World"
                        .equals(node.getData().spanData().getName()));
        assertThat(checkoutNode, is(notNullValue()));

        Attributes attributes = checkoutNode.get().getData().spanData().getAttributes();
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME),
                is(notNullValue()));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION),
                is(notNullValue()));
        assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_CLONE_SHALLOW), is(true));
        assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_CLONE_DEPTH), is(2L));
    }

    @Test
    void testPipelineWithoutCheckoutShallowSteps() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "without-checkout-" + jobNameSuffix.incrementAndGet();

        String pipelineScript = """
            node() {
              stage('foo') {
                checkout([$class: 'GitSCM', branches: [[name: '*/master']], extensions: [], userRemoteConfigs: [[url: 'https://github.com/octocat/Hello-World']]])\s
              }
            }""";
        jenkinsRule.createOnlineSlave();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(
                spans,
                "checkout: github.com/octocat/Hello-World",
                "Stage: foo",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run");
        assertThat(spans.cardinality(), is(8L));

        Optional<Tree.Node<SpanDataWrapper>> checkoutNode =
                spans.breadthFirstSearchNodes(node -> "checkout: github.com/octocat/Hello-World"
                        .equals(node.getData().spanData().getName()));
        Attributes attributes = checkoutNode.get().getData().spanData().getAttributes();

        assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_CLONE_SHALLOW), is(false));
        assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_CLONE_DEPTH), is(0L));
    }

    @Test
    void testFailFastParallelScriptedPipelineWithException() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        String jobName = "fail-fast-parallel-scripted-pipeline-with-failure" + jobNameSuffix.incrementAndGet();

        String pipelineScript = """
            node() {
                stage('ze-parallel-stage') {
                    parallel failingBranch: {
                        error 'the failure that will cause the interruption of other branches'
                    }, branchThatWillBeInterrupted: {
                        sleep 5
                    }, failFast:true
                }
            }""";
        jenkinsRule.createOnlineSlave();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));

        Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(
                spans,
                "sleep",
                "Parallel branch: branchThatWillBeInterrupted",
                "Stage: ze-parallel-stage",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run");

        SpanData sleepSpanData = spans.breadthFirstSearchNodes(
                node -> "sleep".equals(node.getData().spanData().getName()))
            .get()
            .getData()
            .spanData();
        assertThat(sleepSpanData.getStatus().getStatusCode(), is(StatusCode.UNSET));

        SpanData branchThatWillBeInterruptedSpanData = spans.breadthFirstSearchNodes(
                node -> "Parallel branch: branchThatWillBeInterrupted"
                    .equals(node.getData().spanData().getName()))
            .get()
            .getData()
            .spanData();
        assertThat(
                branchThatWillBeInterruptedSpanData.getStatus().getStatusCode(), is(StatusCode.UNSET));
        assertThat(
                branchThatWillBeInterruptedSpanData
                        .getAttributes()
                        .get(ExtendedJenkinsAttributes.JENKINS_STEP_INTERRUPTION_CAUSES),
                is(List.of("FailFastCause: Failed in branch failingBranch")));

        SpanData failingBranchSpanData = spans.breadthFirstSearchNodes(node -> "Parallel branch: failingBranch"
                .equals(node.getData().spanData().getName()))
            .get()
            .getData()
            .spanData();
        assertThat(failingBranchSpanData.getStatus().getStatusCode(), is(StatusCode.ERROR));
        assertThat(
                failingBranchSpanData.getStatus().getDescription(),
                is("the failure that will cause the interruption of other branches"));
    }

    @Test
    void testSpanContextPropagationSynchronousTestStep() throws Exception {
        Set.of(EchoStep.class, EchoStep.DescriptorImpl.class, SpanContextPropagationSynchronousTestStep.class)
                .forEach(c -> System.out.println(c + " -> " + ExtensionList.lookup(c)));

        String pipelineScript = """
            node() {
                stage('ze-stage1') {
                   echo message: 'hello'
                   spanContextPropagationSynchronousTestStep()
                }
            }""";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-SpanContextPropagationSynchronousTestStep-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(
                spans,
                "SpanContextPropagationTestStep.execution",
                "spanContextPropagationSynchronousTestStep",
                "Stage: ze-stage1",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run");
    }

    @Test
    void testSpanContextPropagationSynchronousNonBlockingTestStep() throws Exception {
        Set.of(EchoStep.class, EchoStep.DescriptorImpl.class, SpanContextPropagationSynchronousTestStep.class)
                .forEach(c -> System.out.println(c + " -> " + ExtensionList.lookup(c)));

        String pipelineScript = """
            node() {
                stage('ze-stage1') {
                   echo message: 'hello'
                   spanContextPropagationSynchronousNonBlockingTestStep()
                }
            }""";
        jenkinsRule.createOnlineSlave();

        final String jobName = "test-SpanContextPropagationSynchronousTestStep-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        final Tree<SpanDataWrapper> spans = getBuildTrace();
        checkChainOfSpans(
                spans,
                "SpanContextPropagationSynchronousNonBlockingTestStep.execution",
                "spanContextPropagationSynchronousNonBlockingTestStep",
                "Stage: ze-stage1",
                ExtendedJenkinsAttributes.AGENT_UI,
                "Phase: Run");
    }
}
