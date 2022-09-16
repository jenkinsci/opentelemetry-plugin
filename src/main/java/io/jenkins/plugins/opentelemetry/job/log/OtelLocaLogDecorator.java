package io.jenkins.plugins.opentelemetry.job.log;

import hudson.Extension;
import hudson.model.Queue;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import jenkins.util.JenkinsJVM;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.FileLogStorage;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OtelLocaLogDecorator extends TaskListenerDecorator {
    final String buildFolderPath;

    public OtelLocaLogDecorator(String buildFolderPath) {
        this.buildFolderPath = buildFolderPath;
    }

    @NotNull
    @Override
    public OutputStream decorate(@NotNull OutputStream logger) throws IOException, InterruptedException {
        JenkinsJVM.checkJenkinsJVM();
        File log = new File(buildFolderPath,"log");
        return FileLogStorage.forFile(log).overallListener().getLogger();
    }

    @Extension
    public static final class Factory implements TaskListenerDecorator.Factory {

        private static final Logger LOGGER = Logger.getLogger(OtelLocaLogDecorator.Factory.class.getName());

        @Override
        public boolean isAppliedBeforeMainDecorator() {
            return true;
        }

        @Override
        @Nullable
        public TaskListenerDecorator of(@Nonnull FlowExecutionOwner owner) {
            if (!OpenTelemetrySdkProvider.get().isOtelLogsEnabled()) {
                return null;
            }
            if (!OpenTelemetrySdkProvider.get().isOtelLogsMirrorToDisk()) {
                return null;
            }

            try {
                Queue.Executable executable = owner.getExecutable();
                if (executable instanceof WorkflowRun) {
                    WorkflowRun workflowRun = (WorkflowRun) executable;
                    return new OtelLocaLogDecorator(workflowRun.getRootDir().getPath());
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, ex.getMessage(), ex);
            }
            return null;
        }
    }
}