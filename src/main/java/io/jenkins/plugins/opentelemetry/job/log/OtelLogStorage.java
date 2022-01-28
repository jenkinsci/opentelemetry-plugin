package io.jenkins.plugins.opentelemetry.job.log;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.console.AnnotatedLargeText;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Replaces the logs storage implementation with a custom one
 * that uses OpenTelemetry and Elasticsearch to store and retrieve the logs.
 */
class OtelLogStorage implements LogStorage {

    private final static Logger LOGGER = Logger.getLogger(OtelLogStorage.class.getName());
    final BuildInfo buildInfo;

    public OtelLogStorage(@Nonnull BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    @Nonnull
    @Override
    public BuildListener overallListener() {
        return new OtelLogSenderBuildListener(buildInfo);
    }

    @Nonnull
    @Override
    public TaskListener nodeListener(@Nonnull FlowNode node) {
        //FIXME avoid to pass the node
        return new OtelLogSenderBuildListener(buildInfo, node);
    }

    @Nonnull
    @Override
    public AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(
        @Nonnull FlowExecutionOwner.Executable build, boolean complete) {
        try {
            return new OtelLogRetriever(buildInfo).overallLog(build, complete);
        } catch (Exception x) {
            return new BrokenLogStorage(x).overallLog(build, complete);
        }
    }

    @Nonnull
    @Override
    public AnnotatedLargeText<FlowNode> stepLog(@Nonnull FlowNode flowNode, boolean complete) {
        try {
            return new OtelLogRetriever(buildInfo).stepLog(flowNode, complete);
        } catch (Exception x) {
            return new BrokenLogStorage(x).stepLog(flowNode, complete);
        }
    }

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
    }

    @Override
    public String toString() {
        return "OtelLogStorage{" +
            "buildInfo=" + buildInfo +
            '}';
    }
}