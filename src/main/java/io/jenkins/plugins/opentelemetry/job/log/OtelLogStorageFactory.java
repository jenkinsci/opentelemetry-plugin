/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Queue;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetry;
import io.jenkins.plugins.opentelemetry.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Binds Otel Logs to Pipeline logs.
 * <p>
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/PipelineBridge.java
 */
@Extension
public final class OtelLogStorageFactory implements LogStorageFactory, OpenTelemetryLifecycleListener {

    private final static Logger logger = Logger.getLogger(OtelLogStorageFactory.class.getName());

    static {
        // Make sure JENKINS-52165 is enabled, or performance will be awful for remote shell steps.
        System.setProperty("org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep.USE_WATCHING", "true");
    }

    JenkinsOpenTelemetry jenkinsOpenTelemetry;

    private Tracer tracer;

    static OtelLogStorageFactory get() {
        return ExtensionList.lookupSingleton(OtelLogStorageFactory.class);
    }

    @Nullable
    @Override
    public LogStorage forBuild(@NonNull final FlowExecutionOwner owner) {
        if (!getOpenTelemetrySdkProvider().isLogsEnabled()) {
            logger.log(Level.FINE, () -> "OTel Logs disabled");
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
                Map<String, String> w3cTraceContext = new HashMap<>(monitoringAction.getW3cTraceContext());
                BuildInfo buildInfo = new BuildInfo(run.getParent().getFullName(), run.getNumber(), monitoringAction.getTraceId(), monitoringAction.getSpanId(), w3cTraceContext);
                logger.log(Level.FINEST, () -> "forBuild(" + buildInfo + ")");

                return new OtelLogStorage(buildInfo, tracer, run.getRootDir().getPath());
            } else {
                return null;
            }
        } catch (IOException x) {
            return new BrokenLogStorage(x);
        }
    }

    /**
     * Workaround dependency injection problem. @Inject doesn't work here
     */
    private JenkinsOpenTelemetry getOpenTelemetrySdkProvider() {
        if (jenkinsOpenTelemetry == null) {
            jenkinsOpenTelemetry = JenkinsOpenTelemetry.get();
        }
        return jenkinsOpenTelemetry;
    }

    @Override
    public void afterSdkInitialized(Meter meter, LoggerProvider loggerProvider, EventEmitter eventEmitter, Tracer tracer, ConfigProperties configProperties) {
        this.tracer = tracer;
    }
}