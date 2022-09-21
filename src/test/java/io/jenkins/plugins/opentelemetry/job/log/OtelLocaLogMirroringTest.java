package io.jenkins.plugins.opentelemetry.job.log;

import hudson.ExtensionList;
import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.OpenTelemetryConfiguration;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporterProvider;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Verify.verify;
import static java.util.Optional.of;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

public class OtelLocaLogMirroringTest {

    private static final Logger LOGGER = Logger.getLogger(OtelLocaLogMirroringTest.class.getName());

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();

    static OpenTelemetrySdkProvider openTelemetrySdkProvider;

    static WorkflowJob pipeline;

    static String printedLine = "message_testing_logs_mirroring";
    final static AtomicInteger jobNameSuffix = new AtomicInteger();

    @BeforeClass
    public static void beforeClass() throws Exception {
        LOGGER.log(Level.INFO, "beforeClass()");
        LOGGER.log(Level.INFO, "Wait for jenkins to start...");
        jenkinsRule.waitUntilNoActivity();
        LOGGER.log(Level.INFO, "Jenkins started");

        OpenTelemetryConfiguration.TESTING_INMEMORY_MODE = true;
        ExtensionList<OpenTelemetrySdkProvider> openTelemetrySdkProviders = jenkinsRule.getInstance().getExtensionList(OpenTelemetrySdkProvider.class);
        verify(openTelemetrySdkProviders.size() == 1, "Number of openTelemetrySdkProviders: %s", openTelemetrySdkProviders.size());

        openTelemetrySdkProvider = openTelemetrySdkProviders.get(0);
        openTelemetrySdkProvider.initialize(new OpenTelemetryConfiguration(of("http://localhost:4317"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Collections.emptyMap()));
    }

    @Before
    public void resetOtelConfig() {
        reInitProvider(new HashMap<>());
    }

    @After
    public void after() throws Exception {
        jenkinsRule.waitUntilNoActivity();
        InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE.reset();
        InMemorySpanExporterProvider.LAST_CREATED_INSTANCE.reset();
    }

    private WorkflowRun runBuild() throws Exception {
        String pipelineScript = "pipeline {\n" +
            "  agent any\n" +
            "  stages {\n" +
            "    stage('foo') {\n" +
            "      steps {\n" +
            "        echo '" + printedLine + "' \n" +
            "        script { \n" +
            "          currentBuild.description = 'Bar' \n" +
            "        } \n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";
        pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-logs-mirroring-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        return jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
    }

    private void reInitProvider(Map<String, String> configuration) {
        openTelemetrySdkProvider.initialize(new OpenTelemetryConfiguration(of("http://localhost:4317"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), configuration));
    }

    @Test
    public void return_null_when_mirroring_option_disabled() {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        Map<String, String> configuration = new HashMap<>();
        configuration.put("otel.logs.exporter", "otlp");
        reInitProvider(configuration);

        FlowExecutionOwner flowExecutionOwner = FlowExecutionOwner.dummyOwner();
        TaskListenerDecorator decorator = new OtelLocaLogDecorator.Factory().of(flowExecutionOwner);
        assertNull(decorator);
    }


    @Test
    public void return_decorator_when_mirroring_option_enabled() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        Map<String, String> configuration = new HashMap<>();
        configuration.put("otel.logs.exporter", "otlp");
        configuration.put("otel.logs.mirror_to_disk", "true");
        reInitProvider(configuration);

        WorkflowRun build = runBuild();
        TaskListenerDecorator decorator = new OtelLocaLogDecorator.Factory().of(build.asFlowExecutionOwner());

        assertNotNull(decorator);
        assertEquals(decorator.getClass(), OtelLocaLogDecorator.class);
    }

    @Test
    public void return_null_when_log_exporter_disabled() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        WorkflowRun build = runBuild();
        Map<String, String> configuration = new HashMap<>();
        configuration.put("otel.logs.mirror_to_disk", "true");
        reInitProvider(configuration);

        TaskListenerDecorator decorator = new OtelLocaLogDecorator.Factory().of(build.asFlowExecutionOwner());
        assertNull(decorator);
    }


    @Test
    public void return_otel_log_text_when_otlp_enabled_and_no_log_file() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        Map<String, String> configuration = new HashMap<>();
        configuration.put("otel.logs.exporter", "otlp");
        reInitProvider(configuration);

        WorkflowRun build = runBuild();
        assertEquals(build.getLogText().getClass(), OverallLog.class);
    }


    @Test
    public void return_log_from_file_when_log_file_mirrored() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        Map<String, String> configuration = new HashMap<>();
        configuration.put("otel.logs.exporter", "otlp");
        configuration.put("otel.logs.mirror_to_disk", "true");
        reInitProvider(configuration);

        WorkflowRun build = runBuild();

        assertNotEquals(build.getLogText().getClass(), OverallLog.class);
        String logText = build.getLog();
        assertTrue(logText.contains(printedLine));
    }


    @Test
    public void return_log_from_file_when_log_file_added_before_otlp_enabled() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        WorkflowRun build = runBuild();

        Map<String, String> configuration = new HashMap<>();
        configuration.put("otel.logs.exporter", "otlp");
        reInitProvider(configuration);

        assertNotEquals(build.getLogText().getClass(), OverallLog.class);
        String logText = build.getLog();
        assertTrue(logText.contains(printedLine));
    }
}

