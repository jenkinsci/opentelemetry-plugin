package io.jenkins.plugins.opentelemetry.job.log;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.console.AnnotatedLargeText;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OpenTelemetryConfiguration;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.FileLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Replaces the logs storage implementation with a custom one
 * that uses OpenTelemetry and Elasticsearch to store and retrieve the logs.
 * See https://github.com/jenkinsci/jenkins/blob/master/core/src/main/resources/hudson/model/Run/console.jelly
 * https://github.com/jenkinsci/jenkins/blob/master/core/src/main/resources/hudson/model/Run/consoleFull.jelly
 */
class OtelLogStorage implements LogStorage {

    private final static Logger logger = Logger.getLogger(OtelLogStorage.class.getName());
    final BuildInfo buildInfo;
    final String buildFolderPath;
    final Tracer tracer;

    public OtelLogStorage(@Nonnull BuildInfo buildInfo, @Nonnull Tracer tracer, @Nullable String buildFolderPath) {
        this.buildInfo = buildInfo;
        this.tracer = tracer;
        this.buildFolderPath = buildFolderPath;
    }

    @Nonnull
    @Override
    public BuildListener overallListener() throws IOException, InterruptedException {
        OpenTelemetryConfiguration otelConfiguration = JenkinsOpenTelemetryPluginConfiguration.get().toOpenTelemetryConfiguration();
        Map<String, String> otelConfigurationProperties = otelConfiguration.toOpenTelemetryProperties();
        Map<String, String> otelResourceAttributes = new HashMap<>();
        otelConfiguration.toOpenTelemetryResource().getAttributes().asMap().forEach((k, v) -> otelResourceAttributes.put(k.getKey(), v.toString()));

        OtelLogSenderBuildListener.OtelLogSenderBuildListenerOnController otelLogSenderBuildListenerOnController = new OtelLogSenderBuildListener.OtelLogSenderBuildListenerOnController(buildInfo, otelConfigurationProperties, otelResourceAttributes);

        if (OpenTelemetrySdkProvider.get().isOtelLogsMirrorToDisk()) {
            File logFile = new File(buildFolderPath, "log");
            return new MergedBuildListener(otelLogSenderBuildListenerOnController, FileLogStorage.forFile(logFile).overallListener());
        }
        return otelLogSenderBuildListenerOnController;
    }

    @Nonnull
    @Override
    public TaskListener nodeListener(@Nonnull FlowNode flowNode) throws IOException, InterruptedException {
        OpenTelemetryConfiguration otelConfiguration = JenkinsOpenTelemetryPluginConfiguration.get().toOpenTelemetryConfiguration();
        Map<String, String> otelConfigurationProperties = otelConfiguration.toOpenTelemetryProperties();
        Map<String, String> otelResourceAttributes = new HashMap<>();
        otelConfiguration.toOpenTelemetryResource().getAttributes().asMap().forEach((k, v) -> otelResourceAttributes.put(k.getKey(), v.toString()));
        OtelLogSenderBuildListener.OtelLogSenderBuildListenerOnController otelLogSenderBuildListenerOnController = new OtelLogSenderBuildListener.OtelLogSenderBuildListenerOnController(buildInfo, flowNode.getId(), otelConfigurationProperties, otelResourceAttributes);

        if (OpenTelemetrySdkProvider.get().isOtelLogsMirrorToDisk()) {
            File logFile = new File(buildFolderPath, "log");
            return new MergedTaskListener(otelLogSenderBuildListenerOnController, FileLogStorage.forFile(logFile).nodeListener(flowNode));
        }
        return otelLogSenderBuildListenerOnController;
    }

    /**
     * Invoked by
     * io.jenkins.plugins.opentelemetry.job.log.OtelLogStorage#overallLog(org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner.Executable, boolean)
     * |- org.jenkinsci.plugins.workflow.job.WorkflowRun#getLogText()
     *    |- org.jenkinsci.plugins.workflow.job.WorkflowRun#doConsoleText(org.kohsuke.stapler.StaplerRequest, org.kohsuke.stapler.StaplerResponse)
     *    |- org.jenkinsci.plugins.workflow.job.WorkflowRun#getLog()
     *    |- org.jenkinsci.plugins.workflow.job.WorkflowRun#getLogInputStream()
     *       |- hudson.model.Run#doConsoleText(org.kohsuke.stapler.StaplerRequest, org.kohsuke.stapler.StaplerResponse)
     *       |- hudson.model.Run#writeLogTo(long, org.apache.commons.jelly.XMLOutput)
     *          |- workflowRun/console.jelly
     */
    @Nonnull
    @Override
    public AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(@Nonnull FlowExecutionOwner.Executable build, boolean complete) {
        File logFile = new File(buildFolderPath, "log");
        if (logFile.exists()) {
            return FileLogStorage.forFile(logFile).overallLog(build, complete);
        }

        Span span = tracer.spanBuilder("OtelLogStorage.overallLog")
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, buildInfo.getJobFullName())
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) buildInfo.runNumber)
            .setAttribute("complete", complete)
            .startSpan();
        try (Scope scope = span.makeCurrent()){
            LogStorageRetriever logStorageRetriever = getLogStorageRetriever();
            LogsQueryResult logsQueryResult = logStorageRetriever.overallLog(buildInfo.getJobFullName(), buildInfo.runNumber, buildInfo.getTraceId(), buildInfo.getSpanId(), complete);
            span.setAttribute("completed", logsQueryResult.isComplete());
            return new OverallLog(logsQueryResult.getByteBuffer(), logsQueryResult.getLogsViewHeader(), logsQueryResult.getCharset(), logsQueryResult.isComplete(), build, tracer);
        } catch (Exception x) {
            span.recordException(x);
            return new BrokenLogStorage(x).overallLog(build, complete);
        } finally {
            span.end();
        }
    }

    @Nonnull
    @Override
    public AnnotatedLargeText<FlowNode> stepLog(@Nonnull FlowNode flowNode, boolean complete) {
        File logFile = new File(buildFolderPath, "log");
        if (logFile.exists()) {
            return FileLogStorage.forFile(logFile).stepLog(flowNode, complete);
        }

        Span span = tracer.spanBuilder("OtelLogStorage.stepLog")
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, buildInfo.getJobFullName())
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) buildInfo.runNumber)
            .setAttribute("complete", complete)
            .startSpan();
        try (Scope scope = span.makeCurrent()){
            String traceId = buildInfo.getTraceId();
            String spanId = buildInfo.getSpanId();
            if (traceId == null || spanId == null) {
                throw new IllegalStateException("traceId or spanId is null for " + buildInfo);
            }
            LogStorageRetriever logStorageRetriever = getLogStorageRetriever();
            LogsQueryResult logsQueryResult = logStorageRetriever.stepLog(buildInfo.getJobFullName(), buildInfo.runNumber, flowNode.getId(), traceId, spanId, complete);
            span.setAttribute("completed", logsQueryResult.isComplete())
                .setAttribute("length", logsQueryResult.byteBuffer.length());
            return new AnnotatedLargeText<>(logsQueryResult.getByteBuffer(), logsQueryResult.getCharset(), logsQueryResult.isComplete(), flowNode);
        } catch (Exception x) {
            span.recordException(x);
            return new BrokenLogStorage(x).stepLog(flowNode, complete);
        } finally {
            span.end();
        }
    }
    @SuppressFBWarnings(value = "BC_UNCONFIRMED_CAST", justification = "forBuild only accepts Run")
    @Deprecated
    @Override
    public File getLogFile(FlowExecutionOwner.Executable build, boolean complete) {
        logger.log(Level.FINE, "getLogFile(complete: " + complete + ")");
        Span span = tracer.spanBuilder("OtelLogStorage.getLogFile")
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, buildInfo.getJobFullName())
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) buildInfo.runNumber)
            .setAttribute("complete", complete)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            AnnotatedLargeText<FlowExecutionOwner.Executable> logText = overallLog(build, complete);
            // Not creating a temp file since it would be too expensive to have multiples:
            File f = new File(((Run) build).getRootDir(), "log");
            f.deleteOnExit();
            try (OutputStream os = new FileOutputStream(f)) {
                // Similar to Run#writeWholeLogTo but terminates even if !complete:
                long pos = 0;
                while (true) {
                    long pos2 = logText.writeRawLogTo(pos, os);
                    if (pos2 <= pos) {
                        break;
                    }
                    pos = pos2;
                }
            } catch (Exception x) {
                logger.log(Level.WARNING, null, x);
                span.recordException(x);
            }
            return f;
        } finally {
            span.end();
        }
    }

    @Override
    public String toString() {
        return "OtelLogStorage{" +
            "buildInfo=" + buildInfo +
            '}';
    }

    @Nonnull
    public LogStorageRetriever getLogStorageRetriever() {
        return JenkinsOpenTelemetryPluginConfiguration.get().getLogStorageRetriever();
    }
}