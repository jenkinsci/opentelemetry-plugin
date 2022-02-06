/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Queue;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticsearchLogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryTracesSemanticConventions.SPAN_ID;
import static io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryTracesSemanticConventions.TRACE_ID;

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
                Map<String, String> extContext = monitoringAction.getRootContext();
                extContext.put(TRACE_ID, monitoringAction.getTraceId());
                extContext.put(SPAN_ID, monitoringAction.getSpanId());
                BuildInfo buildInfo = new BuildInfo(run.getParent().getFullName(), run.getNumber(), extContext);
                LOGGER.log(Level.FINE, () -> "forBuild(" + buildInfo + ")");
                // FIXME inject LogStorageRetriever from config
                final ElasticsearchLogStorageRetriever logStorageRetriever = new ElasticsearchLogStorageRetriever();
                return logStoragesByBuild.computeIfAbsent(buildInfo, k -> new OtelLogStorage(buildInfo, logStorageRetriever));
            } else {
                return null;
            }
        } catch (IOException x) {
            return new BrokenLogStorage(x);
        }
    }

    void close(BuildInfo buildInfo) {
        Object removed = this.logStoragesByBuild.remove(buildInfo);
        if (removed == null) {
            LOGGER.log(Level.WARNING, () -> "Failure to close log storage for " + buildInfo + ", storage not found");
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