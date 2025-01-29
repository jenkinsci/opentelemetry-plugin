package io.jenkins.plugins.opentelemetry.job.log;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.console.AnnotatedLargeText;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.jenkins.plugins.opentelemetry.job.log.util.TeeBuildListener;
import io.jenkins.plugins.opentelemetry.job.log.util.TeeOutputStreamBuildListener;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jenkins.util.BuildListenerAdapter;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.FileLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.OutputStreamTaskListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Optional;
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
    final Run run;
    final RunTraceContext runTraceContext;
    final String runFolderPath;
    final Tracer tracer;

    final OtelTraceService otelTraceService;

    public OtelLogStorage(@NonNull Run run, @NonNull OtelTraceService otelTraceService, @NonNull Tracer tracer) {
        this.run = run;
        MonitoringAction monitoringAction = Optional
            .ofNullable(run.getAction(MonitoringAction.class))
            .orElseThrow(() ->  new IllegalStateException("No MonitoringAction found for " + run));

        this.runTraceContext = new RunTraceContext(
            run.getParent().getFullName(),
            run.getNumber(),
            monitoringAction.getTraceId(),
            monitoringAction.getSpanId(),
            monitoringAction.getW3cTraceContext());
        this.otelTraceService = otelTraceService;
        this.tracer = tracer;
        this.runFolderPath =  run.getRootDir().getPath();
    }

    @NonNull
    @Override
    public BuildListener overallListener() throws IOException {
        OtelLogSenderBuildListener otelLogSenderBuildListener = new OtelLogSenderBuildListener.OtelLogSenderBuildListenerOnController(runTraceContext);

        BuildListener result;
        if (JenkinsControllerOpenTelemetry.get().isOtelLogsMirrorToDisk()) {
            try {
                File logFile = new File(runFolderPath, "log");
                BuildListener fileStorageBuildListener = FileLogStorage.forFile(logFile).overallListener();
                if (fileStorageBuildListener instanceof OutputStreamTaskListener) {
                    result = new TeeOutputStreamBuildListener(otelLogSenderBuildListener, fileStorageBuildListener);
                } else {
                    logger.log(Level.INFO, () -> "overallListener(): FileLogStorage's TaskListener is not a OutputStreamTaskListener, use TeeBuildListener for " + fileStorageBuildListener);
                    result = new TeeBuildListener(otelLogSenderBuildListener, fileStorageBuildListener);
                }
            } catch (IOException | InterruptedException e) {
                throw new IOException("Failure creating the mirror logs.", e);
            }
        } else {
            result = otelLogSenderBuildListener;
        }

        return result;
    }

    /**
     * @param flowNode a running node
     * @return a {@link BuildListener} rather than a {@link hudson.model.TaskListener} because the caller of
     * {@link LogStorage#nodeListener(FlowNode)} will wrap any {@link hudson.model.TaskListener} into a {@link BuildListener}
     * causing problems in {@link OutputStreamTaskListener#getOutputStream(TaskListener)}
     * @throws IOException
     */
    @NonNull
    @Override
    public BuildListener nodeListener(@NonNull FlowNode flowNode) throws IOException {
        Span span = otelTraceService.getSpan(run, flowNode);
        FlowNodeTraceContext flowNodeTraceContext = FlowNodeTraceContext.newFlowNodeTraceContext(run, flowNode, span);
        OtelLogSenderBuildListener otelLogSenderBuildListener = new OtelLogSenderBuildListener.OtelLogSenderBuildListenerOnController(flowNodeTraceContext);

        BuildListener result;
        if (JenkinsControllerOpenTelemetry.get().isOtelLogsMirrorToDisk()) {
            try {
                File logFile = new File(runFolderPath, "log");
                BuildListener fileStorageBuildListener = BuildListenerAdapter.wrap(FileLogStorage.forFile(logFile).nodeListener(flowNode));
                if (fileStorageBuildListener instanceof OutputStreamTaskListener) {
                    result = new TeeOutputStreamBuildListener(otelLogSenderBuildListener, fileStorageBuildListener);
                } else {
                    logger.log(Level.INFO, () -> "nodeListener(): FileLogStorage's TaskListener is not a OutputStreamTaskListener, use TeeBuildListener for " + fileStorageBuildListener);
                    result = new TeeBuildListener(otelLogSenderBuildListener, fileStorageBuildListener);
                }
            } catch (IOException | InterruptedException e) {
                throw new IOException("Failure creating the mirror logs.", e);
            }
        } else {
            result = otelLogSenderBuildListener;
        }

        return result;
    }

    /**
     * Invoked by
     * io.jenkins.plugins.opentelemetry.job.log.OtelLogStorage#overallLog(org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner.Executable, boolean)
     * |- org.jenkinsci.plugins.workflow.job.WorkflowRun#getLogText()
     *    |- org.jenkinsci.plugins.workflow.job.WorkflowRun#doConsoleText(org.kohsuke.stapler.StaplerRequest2, org.kohsuke.stapler.StaplerResponse2)
     *    |- org.jenkinsci.plugins.workflow.job.WorkflowRun#getLog()
     *    |- org.jenkinsci.plugins.workflow.job.WorkflowRun#getLogInputStream()
     *       |- hudson.model.Run#doConsoleText(org.kohsuke.stapler.StaplerRequest2, org.kohsuke.stapler.StaplerResponse2)
     *       |- hudson.model.Run#writeLogTo(long, org.apache.commons.jelly.XMLOutput)
     *          |- workflowRun/console.jelly
     */
    @NonNull
    @Override
    public AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(@NonNull FlowExecutionOwner.Executable build, boolean complete) {
        File logFile = new File(runFolderPath, "log");
        if (logFile.exists()) {
            return FileLogStorage.forFile(logFile).overallLog(build, complete);
        }

        Span span = tracer.spanBuilder("OtelLogStorage.overallLog")
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_ID, run.getParent().getFullName())
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_NUMBER, (long) run.getNumber())
            .setAttribute("complete", complete)
            .startSpan();
        try (Scope ignored = span.makeCurrent()){
            LogStorageRetriever logStorageRetriever = getLogStorageRetriever();
            Instant startTime = Instant.ofEpochMilli(run.getStartTimeInMillis());
            Instant endTime = run.getDuration() == 0 ? null : startTime.plusMillis(run.getDuration());
            LogsQueryResult logsQueryResult = logStorageRetriever.overallLog(run.getParent().getFullName(), run.getNumber(), runTraceContext.getTraceId(), runTraceContext.getSpanId(), complete, startTime, endTime);
            span.setAttribute("completed", logsQueryResult.isComplete());
            return new OverallLog(logsQueryResult.getByteBuffer(), logsQueryResult.getLogsViewHeader(), logsQueryResult.getCharset(), logsQueryResult.isComplete(), build, tracer);
        } catch (Exception x) {
            span.recordException(x);
            return new BrokenLogStorage(x).overallLog(build, complete);
        } finally {
            span.end();
        }
    }

    @NonNull
    @Override
    public AnnotatedLargeText<FlowNode> stepLog(@NonNull FlowNode flowNode, boolean complete) {
        File logFile = new File(runFolderPath, "log");
        if (logFile.exists()) {
            return FileLogStorage.forFile(logFile).stepLog(flowNode, complete);
        }

        Span span = tracer.spanBuilder("OtelLogStorage.stepLog")
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_ID, run.getParent().getFullName())
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_NUMBER, (long) run.getNumber())
            .setAttribute("complete", complete)
            .startSpan();
        try (Scope ignored = span.makeCurrent()){
            String traceId = runTraceContext.getTraceId();
            String spanId = runTraceContext.getSpanId();
            if (traceId == null || spanId == null) {
                throw new IllegalStateException("traceId or spanId is null for " + run);
            }
            LogStorageRetriever logStorageRetriever = getLogStorageRetriever();
            Instant startTime = Instant.ofEpochMilli(run.getStartTimeInMillis());
            Instant endTime = run.getDuration() == 0 ? null : startTime.plusMillis(run.getDuration());
            LogsQueryResult logsQueryResult = logStorageRetriever.stepLog(run.getParent().getFullName(), run.getNumber(), flowNode.getId(), traceId, spanId, complete, startTime, endTime);
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
    public File getLogFile(@NonNull FlowExecutionOwner.Executable build, boolean complete) {
        logger.log(Level.FINE, "getLogFile(complete: " + complete + ")");
        Span span = tracer.spanBuilder("OtelLogStorage.getLogFile")
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_ID, run.getParent().getFullName())
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_NUMBER, (long) run.getNumber())
            .setAttribute("complete", complete)
            .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            AnnotatedLargeText<FlowExecutionOwner.Executable> logText = overallLog(build, complete);
            // Not creating a temp file since it would be too expensive to have multiples:
            File f = new File(((Run<?, ?>) build).getRootDir(), "log");
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
            "context=" + runTraceContext +
            '}';
    }

    @NonNull
    public LogStorageRetriever getLogStorageRetriever() {
        return JenkinsOpenTelemetryPluginConfiguration.get().getLogStorageRetriever();
    }
}