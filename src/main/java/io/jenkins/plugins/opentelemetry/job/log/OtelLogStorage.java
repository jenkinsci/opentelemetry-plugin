package io.jenkins.plugins.opentelemetry.job.log;

import hudson.console.AnnotatedLargeText;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Replaces the logs storage implementation with a custom one
 * that uses OpenTelemetry and Elasticsearch to store and retrieve the logs.
 */
class OtelLogStorage implements LogStorage {

    private final static Logger logger = Logger.getLogger(OtelLogStorage.class.getName());
    final BuildInfo buildInfo;
    final LogStorageRetriever logStorageRetriever;
    final Map<TraceAndSpanId, LogsQueryContext> logsQueryContexts = new ConcurrentHashMap<>();

    static class TraceAndSpanId {
        final String traceId;
        final String spanId;

        public TraceAndSpanId(String traceId, String spanId) {
            this.traceId = traceId;
            this.spanId = spanId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TraceAndSpanId that = (TraceAndSpanId) o;
            return Objects.equals(traceId, that.traceId) && Objects.equals(spanId, that.spanId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(traceId, spanId);
        }
    }

    static class TraceAndSpanAndFlowNodeId extends TraceAndSpanId {
        final String flowNodeId;

        public TraceAndSpanAndFlowNodeId(@Nonnull String traceId, @Nonnull String spanId, @Nonnull String flowNodeId) {
            super(traceId, spanId);
            this.flowNodeId = flowNodeId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            TraceAndSpanAndFlowNodeId that = (TraceAndSpanAndFlowNodeId) o;
            return flowNodeId.equals(that.flowNodeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), flowNodeId);
        }
    }

    public OtelLogStorage(@Nonnull BuildInfo buildInfo, LogStorageRetriever logStorageRetriever) {
        this.buildInfo = buildInfo;
        this.logStorageRetriever = logStorageRetriever;
    }

    @Nonnull
    @Override
    public BuildListener overallListener() {
        return new OtelLogSenderBuildListener(buildInfo);
    }

    @Nonnull
    @Override
    public TaskListener nodeListener(@Nonnull FlowNode node) {
        return new OtelLogSenderBuildListener(buildInfo, node);
    }

    @Nonnull
    @Override
    public AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(@Nonnull FlowExecutionOwner.Executable build, boolean complete) {
        logger.log(Level.INFO, ()-> "overallLog(" + buildInfo);
        try {
            TraceAndSpanId traceAndSpanId = new TraceAndSpanId(buildInfo.getTraceId(), buildInfo.getSpanId());
            LogsQueryResult logsQueryResult = logStorageRetriever.overallLog(buildInfo.getTraceId(), buildInfo.getSpanId(), logsQueryContexts.get(traceAndSpanId));
            logsQueryContexts.put(traceAndSpanId, logsQueryResult.getLogsQueryContext());
            return new AnnotatedLargeText<>(logsQueryResult.getByteBuffer(), logsQueryResult.getCharset(), logsQueryResult.isComplete(), build);
        } catch (Exception x) {
            return new BrokenLogStorage(x).overallLog(build, complete);
        }
    }

    @Nonnull
    @Override
    public AnnotatedLargeText<FlowNode> stepLog(@Nonnull FlowNode flowNode, boolean complete) {
        try {
            TraceAndSpanAndFlowNodeId traceAndSpanAndFlowNodeId = new TraceAndSpanAndFlowNodeId(buildInfo.getTraceId(), buildInfo.getSpanId(), flowNode.getId());
            LogsQueryResult logsQueryResult = logStorageRetriever.stepLog(buildInfo.getTraceId(), buildInfo.getSpanId(), logsQueryContexts.get(traceAndSpanAndFlowNodeId));
            logsQueryContexts.put(traceAndSpanAndFlowNodeId, logsQueryResult.getLogsQueryContext());
            return new AnnotatedLargeText<>(logsQueryResult.getByteBuffer(), logsQueryResult.getCharset(), logsQueryResult.isComplete(), flowNode);
        } catch (Exception x) {
            return new BrokenLogStorage(x).stepLog(flowNode, complete);
        }
    }

    /*
    //TODO check if it is really needed to download the plain text console log
    @Nonnull
    @SuppressFBWarnings(value = "BC_UNCONFIRMED_CAST", justification = "forBuild only accepts Run")
    @Deprecated
    @Override
    public File getLogFile(@Nonnull FlowExecutionOwner.Executable build, boolean complete) {
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
            LOGGER.log(Level.WARNING, null, x);
        }
        return f;
    }*/

    @Override
    public String toString() {
        return "OtelLogStorage{" +
            "buildInfo=" + buildInfo +
            '}';
    }
}