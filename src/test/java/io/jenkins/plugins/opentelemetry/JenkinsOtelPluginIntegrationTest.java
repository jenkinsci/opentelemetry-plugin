package io.jenkins.plugins.opentelemetry;

import hudson.model.Result;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.*;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class JenkinsOtelPluginIntegrationTest {
    private final static Logger LOGGER = Logger.getLogger(JenkinsOtelPluginIntegrationTest.class.getName());

    static AtomicInteger jobNameSuffix = new AtomicInteger();
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();

    JenkinsOtelPlugin jenkinsOtelPlugin;
    InMemoryMetricExporter inMemoryMetricExporter;
    InMemorySpanExporter inMemorySpanExporter;

    @Before
    public void before() throws Exception {
        jenkinsRule.waitUntilNoActivity();

        this.inMemoryMetricExporter =  InMemoryMetricExporter.create();
        this.inMemorySpanExporter = InMemorySpanExporter.create();
        this.jenkinsOtelPlugin =  jenkinsRule.getInstance().getExtensionList(JenkinsOtelPlugin.class).get(0);
        jenkinsOtelPlugin.initialize(inMemorySpanExporter, inMemoryMetricExporter);
    }

    @After
    public void after(){
        CompletableResultCode completableResultCode = jenkinsOtelPlugin.getOpenTelemetry().getTracerManagement().forceFlush();
        completableResultCode.join(30, TimeUnit.SECONDS);
        // FIXME flush metrics
    }

    @Test
    public void testSimplePipeline() throws Exception {
        String pipelineScript = "node() {\n" +
                "    stage('ze-stage1') {\n" +
                "       echo 'ze-echo' \n" +
                "    }\n" +
                "    stage('ze-stage2') {\n" +
                "       echo 'ze-echo2' \n" +
                "    }\n" +
                "}";
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-simple-pipeline-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        List<SpanData> finishedSpanItems = flush();
        System.out.println(finishedSpanItems.size());
        List<String> spansAsString = finishedSpanItems.stream().map(spanData -> spanData.getStartEpochNanos() + " - " + spanData.getName() + ", id: " + spanData.getSpanId() + ", parentId: " + spanData.getParentSpanId()).collect(Collectors.toList());
        Collections.sort(spansAsString);

        System.out.println(spansAsString.stream().collect(Collectors.joining(", \n")));
    }

    protected List<SpanData> flush() {
        CompletableResultCode completableResultCode = this.jenkinsOtelPlugin.getOpenTelemetry().getTracerManagement().forceFlush();
        completableResultCode.join(1, TimeUnit.SECONDS);
        return this.inMemorySpanExporter.getFinishedSpanItems();
    }

    @Test
    public void testPipelineWithWrappingStep() throws Exception {
        String pipelineScript = "node() {\n" +
                "    stage('ze-stage1') {\n" +
                "       withEnv(['MY_VARIABLE=MY_VALUE']) {\n" +
                "          echo 'ze-echo' \n" +
                "       }\n" +
                "       echo 'ze-echo' \n" +
                "    }\n" +
                "    stage('ze-stage2') {\n" +
                "       echo 'ze-echo2' \n" +
                "    }\n" +
                "}";
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-simple-pipeline-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        List<SpanData> finishedSpanItems = flush();
        System.out.println(finishedSpanItems.size());
        List<String> spansAsString = finishedSpanItems.stream().map(spanData -> spanData.getStartEpochNanos() + " - " + spanData.getName() + ", id: " + spanData.getSpanId() + ", parentId: " + spanData.getParentSpanId()).collect(Collectors.toList());
        Collections.sort(spansAsString);

        System.out.println(spansAsString.stream().collect(Collectors.joining(", \n")));
    }
    @Test
    public void testPipelineWithError() throws Exception {
        String pipelineScript = "node() {\n" +
                "    stage('ze-stage1') {\n" +
                "       echo 'ze-echo' \n" +
                "    }\n" +
                "    stage('ze-stage2') {\n" +
                "       echo 'ze-echo2' \n" +
                "       error 'ze-pipeline-error' \n" +
                "    }\n" +
                "}";
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-pipeline-with-failure" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.FAILURE, pipeline.scheduleBuild2(0));

        List<SpanData> finishedSpanItems = flush();
        System.out.println(finishedSpanItems.size());
        List<String> spansAsString = finishedSpanItems.stream().map(spanData -> spanData.getStartEpochNanos() + " - " + spanData.getName() + ", id: " + spanData.getSpanId() + ", parentId: " + spanData.getParentSpanId()).collect(Collectors.toList());
        Collections.sort(spansAsString);

        System.out.println(spansAsString.stream().collect(Collectors.joining(", \n")));
    }

    @Test
    public void testPipelineWithParallelStep() throws Exception {
        String pipelineScript = "node {\n" +
                "    stage('ze-parallel-stage') {\n" +
                "        parallel parallelBranch1: {\n" +
                "            echo('this is the parallel-branch-1')\n" +
                "        } ,parallelBranch2: {\n" +
                "            echo('this is the parallel-branch-2')\n" +
                "        } ,parallelBranch3: {\n" +
                "            echo('this is the parallel-branch-3')\n" +
                "        }\n" +
                "    }\n" +
                "}";
        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-pipeline-with-parallel-step" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        List<SpanData> finishedSpanItems = flush();
        System.out.println(finishedSpanItems.size());
        List<String> spansAsString = finishedSpanItems.stream().map(spanData -> spanData.getStartEpochNanos() + " - " + spanData.getName() + ", id: " + spanData.getSpanId() + ", parentId: " + spanData.getParentSpanId()).collect(Collectors.toList());
        Collections.sort(spansAsString);

        System.out.println(spansAsString.stream().collect(Collectors.joining(", \n")));

        //Thread.sleep(Integer.MAX_VALUE);
    }

}
