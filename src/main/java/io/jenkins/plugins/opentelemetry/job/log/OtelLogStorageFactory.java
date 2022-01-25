/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.Main;
import hudson.console.AnnotatedLargeText;
import hudson.model.BuildListener;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    static {
        // Make sure JENKINS-52165 is enabled, or performance will be awful for remote shell steps.
        System.setProperty("org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep.USE_WATCHING", "true");
    }

    private final static Logger LOGGER = Logger.getLogger(OtelLogStorageFactory.class.getName());

    ConcurrentMap<BuildInfo, LogStorage> logStoragesByBuild = new ConcurrentHashMap<>();

    @Nullable
    @Override
    public LogStorage forBuild(@Nonnull final FlowExecutionOwner owner) {
        if (Main.isUnitTest) {
            return null;
        }
        try {
            Queue.Executable exec = owner.getExecutable();
            if (exec instanceof Run) {
                Run<?, ?> run = (Run<?, ?>) exec;
                BuildInfo buildInfo = new BuildInfo(run.getParent().getFullName(), run.getId());
                LOGGER.log(Level.FINE, () -> "forBuild(" + buildInfo + ")" );
                return logStoragesByBuild.computeIfAbsent(buildInfo, k -> new OtelLogStorage(buildInfo));
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

    static class OtelLogStorage implements LogStorage {

        final BuildInfo buildInfo;

        public OtelLogStorage(@Nonnull BuildInfo buildInfo) {
            this.buildInfo= buildInfo;
        }

        @Nonnull
        @Override
        public BuildListener overallListener() {
            return new AbstractOtelLogSender.MasterOtelLogSender(buildInfo);
        }

        @Nonnull
        @Override
        public TaskListener nodeListener(@Nonnull FlowNode node) {
            return new AbstractOtelLogSender.NodeOtelLogSender(buildInfo, node);
        }

        @Nonnull
        @Override
        public AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(@Nonnull FlowExecutionOwner.Executable build, boolean complete) {
            ByteBuffer buffer = new ByteBuffer();
            try {
                buffer.write("FIXME".getBytes(StandardCharsets.UTF_8));// FIXME
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return new AnnotatedLargeText<>(buffer, StandardCharsets.UTF_8, true, build);
        }

        @Nonnull
        @Override
        public AnnotatedLargeText<FlowNode> stepLog(@Nonnull FlowNode node, boolean complete) {
            ByteBuffer buffer = new ByteBuffer();
            try {
                buffer.write("FIXME".getBytes(StandardCharsets.UTF_8));// FIXME
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return new AnnotatedLargeText<>(buffer, StandardCharsets.UTF_8, true, node);
        }

        @Override
        public String toString() {
            return "OtelLogStorage{" +
                "buildInfo=" + buildInfo +
                '}';
        }
    }

    static OtelLogStorageFactory get() {
        return ExtensionList.lookupSingleton(OtelLogStorageFactory.class);
    }

}
