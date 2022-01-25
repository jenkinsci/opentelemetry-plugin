/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.console.AnnotatedLargeText;
import hudson.model.BuildListener;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.opentelemetry.sdk.logs.LogEmitter;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Binds Otel Logs to Pipeline logs.
 * <p>
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/PipelineBridge.java
 */
@Extension
public final class OtelLogStorageFactory implements LogStorageFactory {

    private final static Logger LOGGER = Logger.getLogger(OtelLogStorageFactory.class.getName());

    private OpenTelemetrySdkProvider openTelemetrySdkProvider;

    ConcurrentMap<BuildInfo, LogStorage> logStoragesByBuild = new ConcurrentHashMap<>();

    @Nullable
    @Override
    public LogStorage forBuild(@Nonnull final FlowExecutionOwner owner) {
        try {
            Queue.Executable exec = owner.getExecutable();
            if (exec instanceof Run) {
                Run<?, ?> run = (Run<?, ?>) exec;
                BuildInfo buildInfo = new BuildInfo(run.getParent().getFullName(), run.getId());
                return logStoragesByBuild.computeIfAbsent(buildInfo, k -> new OtelLogStorage(buildInfo, openTelemetrySdkProvider.getLogEmitter()));
            } else {
                return null;
            }
        } catch (IOException x) {
            return new BrokenLogStorage(x);
        }
    }

    void close (BuildInfo buildInfo) {
        Object removed = this.logStoragesByBuild.remove(buildInfo);
        if (removed == null) {
            LOGGER.log(Level.WARNING, () -> "Failure to close log storage for " + buildInfo + ", storage not found");
        }
    }

    @Inject
    public void setOpenTelemetrySdkProvider(OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.openTelemetrySdkProvider = openTelemetrySdkProvider;
    }


    static class OtelLogStorage implements LogStorage {

        final BuildInfo buildInfo;
        final LogEmitter logEmitter;

        public OtelLogStorage(@Nonnull BuildInfo buildInfo, @Nonnull LogEmitter logEmitter) {
            this.buildInfo= buildInfo;
            this.logEmitter = logEmitter;
        }

        @Nonnull
        @Override
        public BuildListener overallListener() {
            return new OtelLogAbstractSender.MasterSender(buildInfo, logEmitter);
        }

        @Nonnull
        @Override
        public TaskListener nodeListener(@Nonnull FlowNode node) {
            return new OtelLogAbstractSender.NodeSender(buildInfo, node, logEmitter);
        }

        @Nonnull
        @Override
        public AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(@Nonnull FlowExecutionOwner.Executable build, boolean complete) {
            throw new UnsupportedOperationException(); // FIXME implement
        }

        @Nonnull
        @Override
        public AnnotatedLargeText<FlowNode> stepLog(@Nonnull FlowNode node, boolean complete) {
            throw new UnsupportedOperationException(); // FIXME implement
        }
    }
    static OtelLogStorageFactory get() {
        return ExtensionList.lookupSingleton(OtelLogStorageFactory.class);
    }

}
