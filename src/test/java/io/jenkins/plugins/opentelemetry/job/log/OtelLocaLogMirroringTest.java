package io.jenkins.plugins.opentelemetry.job.log;

import static com.google.common.base.Verify.verify;
import static java.util.Optional.of;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assertions.*;

import hudson.ExtensionList;
import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.OpenTelemetryConfiguration;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporterProvider;
import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class OtelLocaLogMirroringTest {

    private static final Logger LOGGER = Logger.getLogger(OtelLocaLogMirroringTest.class.getName());

    private static JenkinsRule jenkinsRule;

    private static ReconfigurableOpenTelemetry openTelemetry;
    private static JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    private static WorkflowJob pipeline;

    private static final String PRINTED_LINE = "message_testing_logs_mirroring";
    private static final AtomicInteger jobNameSuffix = new AtomicInteger();

    @BeforeAll
    static void beforeClass(JenkinsRule rule) throws Exception {
        jenkinsRule = rule;
        LOGGER.log(Level.INFO, "beforeClass()");
        LOGGER.log(Level.INFO, "Wait for jenkins to start...");
        jenkinsRule.waitUntilNoActivity();
        LOGGER.log(Level.INFO, "Jenkins started");

        OpenTelemetryConfiguration.TESTING_INMEMORY_MODE = true;
        OtelTraceService.STRICT_MODE = true;
        ExtensionList<JenkinsControllerOpenTelemetry> jenkinsOpenTelemetries =
                jenkinsRule.getInstance().getExtensionList(JenkinsControllerOpenTelemetry.class);
        verify(
                jenkinsOpenTelemetries.size() == 1,
                "Number of jenkinsControllerOpenTelemetrys: %s",
                jenkinsOpenTelemetries.size());

        openTelemetry = ExtensionList.lookup(ReconfigurableOpenTelemetry.class).get(0);
        jenkinsControllerOpenTelemetry = jenkinsOpenTelemetries.get(0);
        jenkinsControllerOpenTelemetry.initialize(new OpenTelemetryConfiguration(
                of("http://localhost:4317"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Collections.emptyMap()));
    }

    @AfterAll
    static void afterClass() throws Exception {
        ((AutoCloseable) openTelemetry).close();
        GlobalOpenTelemetry.resetForTest();
    }

    @BeforeEach
    void resetOtelConfig() {
        reInitProvider(new HashMap<>());
    }

    @AfterEach
    void after() throws Exception {
        jenkinsRule.waitUntilNoActivity();
        InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE.reset();
        InMemorySpanExporterProvider.LAST_CREATED_INSTANCE.reset();
    }

    private WorkflowRun runBuild() throws Exception {
        String pipelineScript = "pipeline {\n" + "  agent any\n"
                + "  stages {\n"
                + "    stage('foo') {\n"
                + "      steps {\n"
                + "        echo '"
                + PRINTED_LINE + "' \n" + "        script { \n"
                + "          currentBuild.description = 'Bar' \n"
                + "        } \n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}";
        pipeline =
                jenkinsRule.createProject(WorkflowJob.class, "test-logs-mirroring-" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        return jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));
    }

    private void reInitProvider(Map<String, String> configuration) {
        jenkinsControllerOpenTelemetry.initialize(new OpenTelemetryConfiguration(
                of("http://localhost:4317"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                configuration));
    }

    @Test
    void return_otel_log_text_when_otlp_enabled_and_no_log_file() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        Map<String, String> configuration = new HashMap<>();
        configuration.put("otel.logs.exporter", "otlp");
        reInitProvider(configuration);

        WorkflowRun build = runBuild();
        assertEquals(OverallLog.class, build.getLogText().getClass());

        assertTrue(build.getLog().isEmpty());

        File logIndex = new File(build.getRootDir().getPath(), "log-index");
        File log = new File(build.getRootDir().getPath(), "log");
        assertFalse(log.exists());
        assertFalse(logIndex.exists());

        assertTrue(Files.readString(build.getLogFile().toPath()).isEmpty());
    }

    @Test
    void return_log_from_file_when_log_file_mirrored() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        Map<String, String> configuration = new HashMap<>();
        configuration.put("otel.logs.exporter", "otlp");
        configuration.put("otel.logs.mirror_to_disk", "true");
        reInitProvider(configuration);

        WorkflowRun build = runBuild();

        assertNotEquals(OverallLog.class, build.getLogText().getClass());
        String logText = build.getLog();
        assertTrue(logText.contains(PRINTED_LINE));

        File logIndex = new File(build.getRootDir().getPath(), "log-index");
        assertTrue(logIndex.exists());
        assertFalse(logText.isEmpty());

        assertEquals(Files.readString(build.getLogFile().toPath()), logText);
    }

    @Test
    void return_log_from_file_when_log_file_added_before_otlp_enabled() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        WorkflowRun build = runBuild();

        Map<String, String> configuration = new HashMap<>();
        configuration.put("otel.logs.exporter", "otlp");
        reInitProvider(configuration);

        assertNotEquals(OverallLog.class, build.getLogText().getClass());
        String logText = build.getLog();
        assertTrue(logText.contains(PRINTED_LINE));

        File logIndex = new File(build.getRootDir().getPath(), "log-index");
        assertTrue(logIndex.exists());
        assertFalse(logText.isEmpty());

        assertEquals(Files.readString(build.getLogFile().toPath()), logText);
    }
}
