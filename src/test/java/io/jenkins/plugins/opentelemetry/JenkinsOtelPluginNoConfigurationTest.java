package io.jenkins.plugins.opentelemetry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.init.StepExecutionInstrumentationInitializer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporterProvider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;

public class JenkinsOtelPluginNoConfigurationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public BuildWatcher buildWatcher = new BuildWatcher();

    /**
     * Test that the StepExecutionInstrumentationInitializer does nothing when configuration is not set.
     * This test is similar to {@link JenkinsOtelPluginIntegrationTest#testSpanContextPropagationSynchronousNonBlockingTestStep()}
     */
    @Test
    public void test_noOp_when_not_configured() throws Exception {
        String pipelineScript =
                """
            node() {
                stage('ze-stage1') {
                   echo message: 'hello'
                   spanContextPropagationSynchronousNonBlockingTestStep()
                }
            }""";
        j.createOnlineSlave();
        final String jobName = "test-SpanContextPropagationSynchronousTestStep";
        WorkflowJob pipeline = j.createProject(WorkflowJob.class, jobName);
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));

        try (LogRecorder recorder = new LogRecorder()
                .quiet()
                .record(StepExecutionInstrumentationInitializer.class, Level.FINE)
                .capture(10)) {
            j.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            assertThat(
                    recorder.getMessages(),
                    Matchers.hasItem("Instrumenting " + SynchronousNonBlockingStepExecution.class.getName() + "..."));
        }
        CompletableResultCode result = JenkinsControllerOpenTelemetry.get()
                .getOpenTelemetrySdk()
                .getSdkTracerProvider()
                .forceFlush();
        result.join(1, TimeUnit.SECONDS);

        // without specific configuration, no spans should be exported
        assertThat(InMemorySpanExporterProvider.LAST_CREATED_INSTANCE, nullValue());
    }

    /**
     * Make sure a standard pipeline with synchronous non-blocking steps works with {@link StepExecutionInstrumentationInitializer#augment(ExecutorService)}
     */
    @Test
    public void test_standard_pipeline() throws Exception {
        j.createOnlineSlave();
        WorkflowJob pipeline = j.createProject(WorkflowJob.class);
        pipeline.setDefinition(new CpsFlowDefinition(
                """
            node {
                writeFile(file: 'file', text: 'Hello, World!')
                archiveArtifacts('file')
            }
            """,
                true));

        try (LogRecorder recorder = new LogRecorder()
                .quiet()
                .record(StepExecutionInstrumentationInitializer.class, Level.FINE)
                .capture(10)) {
            var build = j.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
            assertThat(build.getArtifacts(), hasSize(1));
            assertThat(
                    recorder.getMessages(),
                    Matchers.hasItem("Instrumenting " + SynchronousNonBlockingStepExecution.class.getName() + "..."));
        }
    }
}
