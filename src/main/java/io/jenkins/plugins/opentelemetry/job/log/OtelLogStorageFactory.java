/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Queue;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.backend.custom.CustomLogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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

    static {
        // Make sure JENKINS-52165 is enabled, or performance will be awful for remote shell steps.
        System.setProperty("org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep.USE_WATCHING", "true");
    }

    ConcurrentMap<BuildInfo, LogStorage> logStoragesByBuild = new ConcurrentHashMap<>();

    OpenTelemetrySdkProvider openTelemetrySdkProvider;

    static OtelLogStorageFactory get() {
        return ExtensionList.lookupSingleton(OtelLogStorageFactory.class);
    }

    @Nullable
    @Override
    public LogStorage forBuild(@Nonnull final FlowExecutionOwner owner) {
        if (!getOpenTelemetrySdkProvider().isOtelLogsEnabled()) {
            LOGGER.log(Level.FINE, () -> "forBuild(): null");
            return null;
        }

        try {
            Queue.Executable exec = owner.getExecutable();
            if (exec instanceof Run) {
                Run<?, ?> run = (Run<?, ?>) exec;
                MonitoringAction monitoringAction = run.getAction(MonitoringAction.class);
                if (monitoringAction == null) {
                    throw new IllegalStateException("No MonitoringAction found for " + run);
                }
                // root context contains traceparent
                Map<String, String> rootContext = monitoringAction.getRootContext();
                if (rootContext == null) {
                    throw new IllegalStateException("MonitoringAction.rootContext is null for " + run);
                }
                Map<String, String> buildInfoContext = new HashMap<>(rootContext);
                BuildInfo buildInfo = new BuildInfo(run.getParent().getFullName(), run.getNumber(), monitoringAction.getTraceId(), monitoringAction.getSpanId(), buildInfoContext);
                LOGGER.log(Level.FINE, () -> "forBuild(" + buildInfo + ")");

                return logStoragesByBuild.computeIfAbsent(buildInfo, k -> new OtelLogStorage(buildInfo, getOpenTelemetrySdkProvider().getTracer()));
            } else {
                return null;
            }
        } catch (IOException x) {
            return new BrokenLogStorage(x);
        }
    }



    void close(BuildInfo buildInfo) {
        LOGGER.log(Level.FINE, () -> "Close logStorage for " + buildInfo.jobFullName + "#" + buildInfo.runNumber);
        Object removed = this.logStoragesByBuild.remove(buildInfo);
        if (removed == null) {
            // FIXME UNDERSTAND WHY THIS HAPPENS MORE THAN EXPECTED
            LOGGER.log(Level.FINE, () -> "Log storage for " + buildInfo + " was already closed");
        }
    }

    /**
     * Workaround dependency injection problem. @Inject doesn't work here
     */
    private OpenTelemetrySdkProvider getOpenTelemetrySdkProvider() {
        if (openTelemetrySdkProvider == null) {
            openTelemetrySdkProvider = OpenTelemetrySdkProvider.get();
        }
        return openTelemetrySdkProvider;
    }

}