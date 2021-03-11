/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.github.rutledgepaulv.prune.Tree;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.model.Node;
import hudson.model.Result;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.*;
import org.jvnet.hudson.test.BuildWatcher;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Verify.verify;

/**
 * Note usage of `def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}}` is inspired by
 * https://github.com/jenkinsci/workflow-basic-steps-plugin/blob/474cea2a53753e1fb9b166fa1ca0f6184b5cee4a/src/test/java/org/jenkinsci/plugins/workflow/steps/IsUnixStepTest.java#L39
 */
public class JenkinsOtelPluginIntegrationTest {
    static {
        OpenTelemetrySdkProvider.TESTING_INMEMORY_MODE = true;
        OpenTelemetrySdkProvider.TESTING_SPAN_EXPORTER = InMemorySpanExporter.create();
        OpenTelemetrySdkProvider.TESTING_METRICS_EXPORTER = InMemoryMetricExporter.create();
    }

    private final static Logger LOGGER = Logger.getLogger(JenkinsOtelPluginIntegrationTest.class.getName());

    final static AtomicInteger jobNameSuffix = new AtomicInteger();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @ClassRule
    @ConfiguredWithCode("jcasc-elastic-backend.yml")
    public static JenkinsConfiguredWithCodeRule jenkinsRule = new JenkinsConfiguredWithCodeRule();

    static OpenTelemetrySdkProvider openTelemetrySdkProvider;


    @BeforeClass
    public static void beforeClass() throws Exception {
        System.out.println("beforeClass()");
        System.out.println("Wait for jenkins to start...");
        jenkinsRule.waitUntilNoActivity();
        System.out.println("Jenkins started");

        ExtensionList<OpenTelemetrySdkProvider> openTelemetrySdkProviders = jenkinsRule.getInstance().getExtensionList(OpenTelemetrySdkProvider.class);
        verify(openTelemetrySdkProviders.size() == 1, "Number of openTelemetrySdkProviders: %s", openTelemetrySdkProviders.size());
        openTelemetrySdkProvider = openTelemetrySdkProviders.get(0);

        // verify(openTelemetrySdkProvider.openTelemetry == null, "OpenTelemetrySdkProvider has already been configured");
        openTelemetrySdkProvider.initializeForTesting();

        // openTelemetrySdkProvider.tracer.setDelegate(openTelemetrySdkProvider.openTelemetry.getTracer("jenkins"));
    }

    @Before
    public void before() throws Exception {
    }

    @After
    public void after() throws Exception {
        jenkinsRule.waitUntilNoActivity();
        ((InMemorySpanExporter) OpenTelemetrySdkProvider.TESTING_SPAN_EXPORTER).reset();
        ((InMemoryMetricExporter) OpenTelemetrySdkProvider.TESTING_METRICS_EXPORTER).reset();
    }

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
        final Node node = jenkinsRule.createOnlineSlave();

        final String jobName = "test-simple-pipeline-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();

        checkChainOfSpans(spans, "Phase: Start", jobName);
        checkChainOfSpans(spans, "shell-1", "Stage: ze-stage1", "Node: Ready", "Node: Allocate", "Phase: Run", jobName);
        checkChainOfSpans(spans, "shell-2", "Stage: ze-stage2", "Node: Ready", "Node: Allocate", "Phase: Run", jobName);
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(10L));

        // WORKAROUND because we don't know how to force the IntervalMetricReader to collect metrics
        Thread.sleep(600);
        Map<String, MetricData> exportedMetrics = ((InMemoryMetricExporter) OpenTelemetrySdkProvider.TESTING_METRICS_EXPORTER).getLastExportedMetricByMetricName();
        dumpMetrics(exportedMetrics);
        MetricData runCompletedCounterData = exportedMetrics.get(JenkinsSemanticMetrics.CI_PIPELINE_RUN_COMPLETED);
        MatcherAssert.assertThat(runCompletedCounterData, CoreMatchers.notNullValue());
        // TODO TEST METRICS WITH PROPER RESET BETWEEN TESTS
        MatcherAssert.assertThat(runCompletedCounterData.getType(), CoreMatchers.is(MetricDataType.LONG_SUM));
        Collection<LongPointData> metricPoints = runCompletedCounterData.getLongSumData().getPoints();
        //MatcherAssert.assertThat(Iterables.getLast(metricPoints).getValue(), CoreMatchers.is(1L));


    }

    private void checkChainOfSpans(Tree<SpanDataWrapper> spanTree, String... expectedSpanNames) {
        final List<String> expectedSpanNamesList = Arrays.asList(expectedSpanNames);
        final Iterator<String> expectedSpanNamesIt = expectedSpanNamesList.iterator();
        if (!expectedSpanNamesIt.hasNext()) {
            Assert.fail("No element in the list of expected spans for " + Arrays.asList(expectedSpanNames));
        }
        final String leafSpanName = expectedSpanNamesIt.next();
        Optional<Tree.Node<SpanDataWrapper>> actualNodeOptional = spanTree.breadthFirstSearchNodes(node -> leafSpanName.equals(node.getData().spanData.getName()));

        if (!actualNodeOptional.isPresent()) {
            // error
        }
        while (expectedSpanNamesIt.hasNext()) {
            String expectedSpanName = expectedSpanNamesIt.next();
            actualNodeOptional = actualNodeOptional.get().getParent();
            MatcherAssert.assertThat("Expected span:" + expectedSpanName + " in chain of span" + expectedSpanNamesIt, actualNodeOptional.get().getData().spanData.getName(), CoreMatchers.is(expectedSpanName));
        }
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
                "        node('bar') { \n" +
                "          echo 'hi bar' \n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        final Node node = jenkinsRule.createOnlineSlave();
        node.setLabelString("bar");

        final String jobName = "test-simple-pipeline-" + jobNameSuffix.incrementAndGet();
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", jobName);
        checkChainOfSpans(spans, "Node: Ready", "Node: Allocate", "Stage: foo", "Phase: Run");
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(7L));
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

        final Node node = jenkinsRule.createOnlineSlave();

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-simple-pipeline-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "shell-1", "Stage: ze-stage1", "Node: Ready", "Node: Allocate", "Phase: Run");
        checkChainOfSpans(spans, "shell-2", "Stage: ze-stage2", "Node: Ready", "Node: Allocate", "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(10L));
    }

    private void dumpMetrics(Map<String, MetricData> exportedMetrics) {
        System.out.println("Metrics: " + exportedMetrics.size());
        System.out.println(exportedMetrics.values().stream().sorted(Comparator.comparing(MetricData::getName)).map(metric -> {
            MetricDataType metricType = metric.getType();
            String s = metric.getName() + "   " + metricType + " ";
            switch (metricType) {
                case LONG_SUM:
                    s += metric.getLongSumData().getPoints().stream().map(point -> String.valueOf(point.getValue())).collect(Collectors.joining(", ")) + "";
                    break;
                case DOUBLE_SUM:
                    s += metric.getDoubleSumData().getPoints().stream().map(point -> String.valueOf(point.getValue())).collect(Collectors.joining(", ")) + "";
                    break;
                case DOUBLE_GAUGE:
                    s += metric.getDoubleGaugeData().getPoints().stream().map(point -> String.valueOf(point.getValue())).collect(Collectors.joining(", ")) + "";
                    break;
                case LONG_GAUGE:
                    s += metric.getLongGaugeData().getPoints().stream().map(point -> String.valueOf(point.getValue())).collect(Collectors.joining(", ")) + "";
                    break;
                case SUMMARY:
                    break;
                default:

            }
            return s;
        }).collect(Collectors.joining(" \n")));
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
        final Node node = jenkinsRule.createOnlineSlave();

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-simple-pipeline-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "shell-1", "Stage: ze-stage1", "Node: Ready", "Node: Allocate", "Phase: Run");
        checkChainOfSpans(spans, "shell-2", "Stage: ze-stage2", "Node: Ready", "Node: Allocate", "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(11L));
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

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-pipeline-with-failure" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "shell-1", "Stage: ze-stage1", "Node: Ready", "Node: Allocate", "Phase: Run");
        checkChainOfSpans(spans, "shell-2", "Stage: ze-stage2", "Node: Ready", "Node: Allocate", "Phase: Run");
        checkChainOfSpans(spans, "error", "Stage: ze-stage2", "Node: Ready", "Node: Allocate", "Phase: Run");
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
        final Node node = jenkinsRule.createOnlineSlave();

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-pipeline-with-parallel-step" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "shell-1", "Parallel branch: parallelBranch1", "Stage: ze-parallel-stage", "Node: Ready", "Node: Allocate", "Phase: Run");
        checkChainOfSpans(spans, "shell-2", "Parallel branch: parallelBranch2", "Stage: ze-parallel-stage", "Node: Ready", "Node: Allocate", "Phase: Run");
        checkChainOfSpans(spans, "shell-3", "Parallel branch: parallelBranch3", "Stage: ze-parallel-stage", "Node: Ready", "Node: Allocate", "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(13L));

    }

    protected Tree<SpanDataWrapper> getGeneratedSpans() {

        CompletableResultCode completableResultCode = this.openTelemetrySdkProvider.getOpenTelemetrySdk().getSdkTracerProvider().forceFlush();
        completableResultCode.join(1, TimeUnit.SECONDS);
        List<SpanData> spans = ((InMemorySpanExporter) OpenTelemetrySdkProvider.TESTING_SPAN_EXPORTER).getFinishedSpanItems();

        final BiPredicate<Tree.Node<SpanDataWrapper>, Tree.Node<SpanDataWrapper>> parentChildMatcher = (spanDataNode1, spanDataNode2) -> {
            final SpanData spanData1 = spanDataNode1.getData().spanData;
            final SpanData spanData2 = spanDataNode2.getData().spanData;
            return Objects.equals(spanData1.getSpanId(), spanData2.getParentSpanId());
        };
        final List<Tree<SpanDataWrapper>> trees = Tree.of(spans.stream().map(span -> new SpanDataWrapper(span)).collect(Collectors.toList()), parentChildMatcher);
        System.out.println("## TREE VIEW OF SPANS ## ");
        for (Tree<SpanDataWrapper> tree : trees) {
            System.out.println(tree);
        }

        return trees.get(0);
    }

    @AfterClass
    public static void afterClass() {
        GlobalOpenTelemetry.resetForTest();
    }

    static class SpanDataWrapper {
        final SpanData spanData;

        public SpanDataWrapper(SpanData spanData) {
            this.spanData = spanData;
        }

        @Override
        public String toString() {
            String result = spanData.getName();

            final Attributes attributes = spanData.getAttributes();
            if (attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE) != null) {
                result += ", function: " + attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE);
            }
            if (attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID) != null) {
                result += ", node.id: " + attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID);
            }
            return result;
        }
    }
}
