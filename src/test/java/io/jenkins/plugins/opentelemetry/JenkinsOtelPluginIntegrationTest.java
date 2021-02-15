/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.model.Result;
import hudson.model.TaskListener;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
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
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Verify.verify;
import static io.jenkins.plugins.opentelemetry.backend.CustomObservabilityBackend.OTEL_CUSTOM_URL;
import static io.jenkins.plugins.opentelemetry.backend.ElasticBackend.OTEL_ELASTIC_URL;
import static io.jenkins.plugins.opentelemetry.backend.JaegerBackend.OTEL_JAEGER_URL;
import static io.jenkins.plugins.opentelemetry.job.OtelEnvironmentContributor.SPAN_ID;
import static io.jenkins.plugins.opentelemetry.job.OtelEnvironmentContributor.TRACE_ID;

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


        String pipelineScript = "node() {\n" +
                "    stage('ze-stage1') {\n" +
                "       sh 'echo ze-echo' \n" +
                "    }\n" +
                "    stage('ze-stage2') {\n" +
                "       sh 'echo ze-echo-2' \n" +
                "    }\n" +
                "}";
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-simple-pipeline-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        List<SpanData> finishedSpanItems = flush();
        dumpSpans(finishedSpanItems);
        MatcherAssert.assertThat(finishedSpanItems.size(), CoreMatchers.is(8));

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

    @Test
    public void testTraceEnvironmentVariablesInjectedInShellSteps() throws Exception {
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
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-trace-environment-variables-injected-in-shell-steps-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        List<SpanData> finishedSpanItems = flush();
        dumpSpans(finishedSpanItems);
        MatcherAssert.assertThat(finishedSpanItems.size(), CoreMatchers.is(6));
    }


    @Test
    public void testPipelineWithSkippedSteps() throws Exception {
        String pipelineScript = "node() {\n" +
                "    stage('ze-stage1') {\n" +
                "       sh 'echo ze-echo' \n" +
                "       echo 'ze-echo-step' \n" +
                "    }\n" +
                "    stage('ze-stage2') {\n" +
                "       sh 'echo ze-echo-2' \n" +
                "    }\n" +
                "}";
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-simple-pipeline-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        List<SpanData> finishedSpanItems = flush();
        dumpSpans(finishedSpanItems);
        MatcherAssert.assertThat(finishedSpanItems.size(), CoreMatchers.is(8));
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

    protected List<SpanData> flush() {
        CompletableResultCode completableResultCode = this.openTelemetrySdkProvider.getOpenTelemetrySdk().getSdkTracerProvider().forceFlush();
        completableResultCode.join(1, TimeUnit.SECONDS);
        return ((InMemorySpanExporter) OpenTelemetrySdkProvider.TESTING_SPAN_EXPORTER).getFinishedSpanItems();
    }

    @Test
    public void testPipelineWithWrappingStep() throws Exception {
        String pipelineScript = "node() {\n" +
                "    stage('ze-stage1') {\n" +
                "       withEnv(['MY_VARIABLE=MY_VALUE']) {\n" +
                "          sh 'echo ze-echo' \n" +
                "       }\n" +
                "       sh 'echo ze-echo' \n" +
                "    }\n" +
                "    stage('ze-stage2') {\n" +
                "       sh 'echo ze-echo2' \n" +
                "    }\n" +
                "}";
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-simple-pipeline-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        List<SpanData> finishedSpanItems = flush();
        dumpSpans(finishedSpanItems);
        MatcherAssert.assertThat(finishedSpanItems.size(), CoreMatchers.is(9));
    }

    @Test
    public void testPipelineWithError() throws Exception {
        String pipelineScript = "node() {\n" +
                "    stage('ze-stage1') {\n" +
                "       sh 'echo ze-echo' \n" +
                "    }\n" +
                "    stage('ze-stage2') {\n" +
                "       sh 'echo ze-echo2' \n" +
                "       error 'ze-pipeline-error' \n" +
                "    }\n" +
                "}";
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-pipeline-with-failure" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));

        List<SpanData> finishedSpanItems = flush();
        dumpSpans(finishedSpanItems);
        MatcherAssert.assertThat(finishedSpanItems.size(), CoreMatchers.is(9));
    }

    @Test
    public void testPipelineWithParallelStep() throws Exception {
        String pipelineScript = "node {\n" +
                "    stage('ze-parallel-stage') {\n" +
                "        parallel parallelBranch1: {\n" +
                "            sh 'echo this-is-the-parallel-branch-1'\n" +
                "        } ,parallelBranch2: {\n" +
                "            sh 'echo this-is-the-parallel-branch-2'\n" +
                "        } ,parallelBranch3: {\n" +
                "            sh 'echo this-is-the-parallel-branch-3'\n" +
                "        }\n" +
                "    }\n" +
                "}";
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-pipeline-with-parallel-step" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        List<SpanData> finishedSpanItems = flush();
        dumpSpans(finishedSpanItems);

        MatcherAssert.assertThat(finishedSpanItems.size(), CoreMatchers.is(11));

    }

    protected void dumpSpans(List<SpanData> finishedSpanItems) {
        System.out.println(finishedSpanItems.size());
        List<String> spansAsString = finishedSpanItems.stream().map(spanData ->
                "   " + spanData.getStartEpochNanos() + " - " + spanData.getName() + ", id: " + spanData.getSpanId() + ", parentId: " + spanData.getParentSpanId() + ", attributes: " + spanData.getAttributes().asMap()
        ).collect(Collectors.toList());
        Collections.sort(spansAsString);

        System.out.println(spansAsString.stream().collect(Collectors.joining(", \n")));
    }

}
